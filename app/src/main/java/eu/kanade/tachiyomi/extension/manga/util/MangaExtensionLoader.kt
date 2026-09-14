package eu.kanade.tachiyomi.extension.manga.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dalvik.system.PathClassLoader
import eu.kanade.domain.extension.manga.interactor.TrustMangaExtension
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.util.lang.Hash
import eu.kanade.tachiyomi.util.storage.copyAndSetReadOnlyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import mihon.app.di.appGraph
import mihon.data.dalvik.DelegateLastClassLoaderCompat
import mihon.domain.extension.manga.interactor.GetMangaExtensionStores
import mihon.domain.extension.model.ContentWarning
import mihon.domain.extension.model.ExtensionStore
import mihon.domain.extension.model.ExtensionStore.Companion.KEIYOUSHI_SIGNATURE
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import java.io.File

/**
 * Class that handles the loading of the extensions. Supports two kinds of extensions:
 *
 * 1. Shared extension: This extension is installed to the system with package
 * installer, so other variants of Tachiyomi/Aniyomi and its forks can also use this extension.
 *
 * 2. Private extension: This extension is put inside private data directory of the
 * Application. Only this app can access this extension.
 */
@SuppressLint("DiscouragedApi")
internal object MangaExtensionLoader {

    private const val EXTENSION_FEATURE = "tachiyomi.extension"
    private const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
    private const val METADATA_SOURCE_FACTORY = "tachiyomi.extension.factory"
    private const val METADATA_NAME = "tachiyomi.extension.name"
    private const val METADATA_EXTENSION_LIB = "tachiyomi.extension.lib"
    private const val METADATA_NSFW = "tachiyomi.extension.nsfw"
    private const val METADATA_CONTENT_WARNING = "tachiyomi.extension.contentWarning"

    const val LIB_VERSION_MIN = 1.2
    const val LIB_VERSION_MAX = 1.5

    private val SUPPORTED_LIB_VERSIONS = listOf(
        LIB_VERSION_MIN,
        1.3,
        1.4,
        LIB_VERSION_MAX,
    )

    private const val PRIVATE_EXTENSION_DIR = "extensions"
    private const val PRIVATE_EXTENSION_EXTENSION = "apk"

    private fun getPrivateExtensionDir(context: Context): File {
        return File(context.filesDir, PRIVATE_EXTENSION_DIR).also { it.mkdirs() }
    }

    private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
        PackageManager.GET_META_DATA or
        PackageManager.GET_SIGNATURES or
        PackageManager.GET_SIGNING_CERTIFICATES

    fun installPrivateExtensionFile(context: Context, file: File): Boolean {
        val extension = context.packageManager.getPackageArchiveInfo(
            file.absolutePath,
            PACKAGE_FLAGS,
        )
            ?.takeIf { isPackageAnExtension(it) } ?: return false
        val currentExtension = getMangaExtensionPackageInfoFromPkgName(
            context,
            extension.packageName,
        )

        if (currentExtension != null) {
            if (PackageInfoCompat.getLongVersionCode(extension) <
                PackageInfoCompat.getLongVersionCode(currentExtension)
            ) {
                logcat(LogPriority.ERROR) { "Installed extension version is higher. Downgrading is not allowed." }
                return false
            }

            val extensionSignatures = getSignatures(extension)
            if (extensionSignatures.isNullOrEmpty()) {
                logcat(LogPriority.ERROR) { "Extension to be installed is not signed." }
                return false
            }

            if (!extensionSignatures.containsAll(getSignatures(currentExtension)!!)) {
                logcat(LogPriority.ERROR) { "Installed extension signature is not matched." }
                return false
            }
        }

        val target = File(
            getPrivateExtensionDir(context),
            "${extension.packageName}.$PRIVATE_EXTENSION_EXTENSION",
        )
        return try {
            target.delete()
            file.copyAndSetReadOnlyTo(target, overwrite = true)
            if (currentExtension != null) {
                MangaExtensionInstallReceiver.notifyReplaced(context, extension.packageName)
            } else {
                MangaExtensionInstallReceiver.notifyAdded(context, extension.packageName)
            }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to copy extension file." }
            target.delete()
            false
        }
    }

    fun uninstallPrivateExtension(context: Context, pkgName: String) {
        File(getPrivateExtensionDir(context), "$pkgName.$PRIVATE_EXTENSION_EXTENSION").delete()
    }

    /**
     * Return a list of all the available extensions initialized concurrently.
     *
     * @param context The application context.
     * @param alreadyLoaded Extensions loaded by an earlier call. Any of these whose apk is unchanged
     * and which still passes every check is returned as is, so its sources keep working and its
     * update status survives. Pass nothing to load every extension from scratch.
     */
    suspend fun loadMangaExtensions(
        context: Context,
        alreadyLoaded: Map<String, MangaExtension.Loaded> = emptyMap(),
    ): List<MangaExtension.Installed> {
        val trustExtension = context.appGraph.trustMangaExtension
        val sourcePreferences = context.appGraph.sourcePreferences
        val enabledContentWarnings = sourcePreferences.enabledContentWarnings.get()
        val applyContentWarningsToInstalled = sourcePreferences.applyContentWarningsToInstalled.get()

        val pkgManager = context.packageManager

        val installedPkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pkgManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()),
            )
        } else {
            pkgManager.getInstalledPackages(PACKAGE_FLAGS)
        }

        val sharedExtPkgs = installedPkgs
            .asSequence()
            .filter { isPackageAnExtension(it) }
            .map { MangaExtensionInfo(packageInfo = it, isShared = true) }

        val privateExtPkgs = getPrivateExtensionDir(context)
            .listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension == PRIVATE_EXTENSION_EXTENSION }
            ?.mapNotNull {
                // Just in case, since Android 14+ requires them to be read-only
                if (it.canWrite()) {
                    it.setReadOnly()
                }

                val path = it.absolutePath
                pkgManager.getPackageArchiveInfo(path, PACKAGE_FLAGS)
                    ?.apply { applicationInfo?.fixBasePaths(path) }
            }
            ?.filter { isPackageAnExtension(it) }
            ?.map { MangaExtensionInfo(packageInfo = it, isShared = false) }
            ?: emptySequence()

        val extPkgs = (sharedExtPkgs + privateExtPkgs)
            // Remove duplicates. Shared takes priority than private by default
            .distinctBy { it.packageInfo.packageName }
            // Compare version number
            .mapNotNull { sharedPkg ->
                val privatePkg = privateExtPkgs
                    .singleOrNull { it.packageInfo.packageName == sharedPkg.packageInfo.packageName }
                selectExtensionPackage(sharedPkg, privatePkg)
            }
            .toList()

        if (extPkgs.isEmpty()) return emptyList()

        // KMK -->
        val repos = context.appGraph.getMangaExtensionStores.await()
        // KMK <--

        // Load each extension concurrently and wait for completion
        return withIOContext {
            extPkgs
                .map {
                    async {
                        loadMangaExtensionCatching(
                            context = context,
                            extensionInfo = it,
                            trustExtension = trustExtension,
                            enabledContentWarnings = enabledContentWarnings,
                            applyContentWarningsToInstalled = applyContentWarningsToInstalled,
                            alreadyLoaded = alreadyLoaded[it.packageInfo.packageName],
                            extRepos = repos,
                        )
                    }
                }
                .awaitAll()
        }
    }

    /**
     * Attempts to load an extension from the given package name. It checks if the extension
     * contains the required feature flag before trying to load it.
     */
    suspend fun loadMangaExtensionFromPkgName(context: Context, pkgName: String): MangaExtension.Installed? {
        val extensionPackage = getMangaExtensionInfoFromPkgName(context, pkgName)
        if (extensionPackage == null) {
            logcat(LogPriority.ERROR) { "Extension package is not found ($pkgName)" }
            return null
        }

        val sourcePreferences = context.appGraph.sourcePreferences
        return loadMangaExtensionCatching(
            context = context,
            extensionInfo = extensionPackage,
            trustExtension = context.appGraph.trustMangaExtension,
            enabledContentWarnings = sourcePreferences.enabledContentWarnings.get(),
            applyContentWarningsToInstalled = sourcePreferences.applyContentWarningsToInstalled.get(),
        )
    }

    fun getMangaExtensionPackageInfoFromPkgName(context: Context, pkgName: String): PackageInfo? {
        return getMangaExtensionInfoFromPkgName(context, pkgName)?.packageInfo
    }

    private fun getMangaExtensionInfoFromPkgName(context: Context, pkgName: String): MangaExtensionInfo? {
        val privateExtensionFile = File(
            getPrivateExtensionDir(context),
            "$pkgName.$PRIVATE_EXTENSION_EXTENSION",
        )
        val privatePkg = if (privateExtensionFile.isFile) {
            context.packageManager.getPackageArchiveInfo(
                privateExtensionFile.absolutePath,
                PACKAGE_FLAGS,
            )
                ?.takeIf { isPackageAnExtension(it) }
                ?.let {
                    it.applicationInfo?.fixBasePaths(privateExtensionFile.absolutePath)
                    MangaExtensionInfo(
                        packageInfo = it,
                        isShared = false,
                    )
                }
        } else {
            null
        }

        val sharedPkg = try {
            context.packageManager.getPackageInfo(pkgName, PACKAGE_FLAGS)
                .takeIf { isPackageAnExtension(it) }
                ?.let {
                    MangaExtensionInfo(
                        packageInfo = it,
                        isShared = true,
                    )
                }
        } catch (error: PackageManager.NameNotFoundException) {
            null
        }

        return selectExtensionPackage(sharedPkg, privatePkg)
    }

    /**
     * [loadMangaExtension] reports the failures it knows how to name, but an apk can be malformed in
     * ways it doesn't check for. Keep anything unforeseen to the extension that caused it instead of
     * letting it take down the load of every other extension.
     */
    private suspend fun loadMangaExtensionCatching(
        context: Context,
        extensionInfo: MangaExtensionInfo,
        trustExtension: TrustMangaExtension,
        enabledContentWarnings: Set<ContentWarning>,
        applyContentWarningsToInstalled: Boolean,
        alreadyLoaded: MangaExtension.Loaded? = null,
        extRepos: List<ExtensionStore>? = null,
    ): MangaExtension.Installed {
        return try {
            loadMangaExtension(
                context = context,
                extensionInfo = extensionInfo,
                trustExtension = trustExtension,
                enabledContentWarnings = enabledContentWarnings,
                applyContentWarningsToInstalled = applyContentWarningsToInstalled,
                alreadyLoaded = alreadyLoaded,
                extRepos = extRepos,
            )
        } catch (e: Throwable) {
            val pkgInfo = extensionInfo.packageInfo
            logcat(LogPriority.ERROR, e) { "Extension load error: ${pkgInfo.packageName}" }
            MangaExtension.NotLoaded(
                name = pkgInfo.packageName,
                pkgName = pkgInfo.packageName,
                versionName = pkgInfo.versionName.orEmpty(),
                versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo),
                isShared = extensionInfo.isShared,
                contentWarning = ContentWarning.SAFE,
                reason = MangaExtension.NotLoaded.Reason.Failed(e.rootMessage, e.stackTraceToString()),
            )
        }
    }

    /**
     * Loads an extension
     *
     * @param context The application context.
     * @param extensionInfo The extension to load.
     */
    private suspend fun loadMangaExtension(
        context: Context,
        extensionInfo: MangaExtensionInfo,
        trustExtension: TrustMangaExtension,
        enabledContentWarnings: Set<ContentWarning>,
        applyContentWarningsToInstalled: Boolean,
        alreadyLoaded: MangaExtension.Loaded? = null,
        extRepos: List<ExtensionStore>? = null,
    ): MangaExtension.Installed {
        val repos = extRepos ?: context.appGraph.getMangaExtensionStores.await()
        val pkgManager = context.packageManager
        val pkgInfo = extensionInfo.packageInfo
        val appInfo = pkgInfo.applicationInfo
        val metaData = appInfo?.metaData
        val pkgName = pkgInfo.packageName

        val extName = metaData?.getString(METADATA_NAME)
            ?: appInfo?.let {
                pkgManager.getApplicationLabel(it).toString().substringAfter(
                    "Tachiyomi: ",
                )
            }
            ?: pkgName
        val versionName = pkgInfo.versionName
        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)
        val contentWarning = when {
            metaData == null -> ContentWarning.SAFE

            metaData.containsKey(METADATA_CONTENT_WARNING) -> {
                when (metaData.getInt(METADATA_CONTENT_WARNING)) {
                    1 -> ContentWarning.MIXED
                    2 -> ContentWarning.NSFW
                    else -> ContentWarning.SAFE
                }
            }

            metaData.getInt(METADATA_NSFW) == 1 -> ContentWarning.NSFW

            else -> ContentWarning.SAFE
        }

        fun notLoaded(
            reason: MangaExtension.NotLoaded.Reason,
            libVersion: Double? = null,
            signatureHash: String? = null,
        ) = MangaExtension.NotLoaded(
            name = extName,
            pkgName = pkgName,
            versionName = versionName.orEmpty(),
            versionCode = versionCode,
            isShared = extensionInfo.isShared,
            contentWarning = contentWarning,
            libVersion = libVersion,
            signatureHash = signatureHash,
            reason = reason,
        )

        if (appInfo == null || metaData == null) {
            logcat(LogPriority.WARN) { "Missing application info for extension $extName" }
            return notLoaded(MangaExtension.NotLoaded.Reason.Malformed)
        }

        if (versionName.isNullOrEmpty()) {
            logcat(LogPriority.WARN) { "Missing versionName for extension $extName" }
            return notLoaded(MangaExtension.NotLoaded.Reason.Malformed)
        }

        // Validate lib version
        val libVersion = metaData.getFloat(METADATA_EXTENSION_LIB)
            .takeUnless { it == 0.0f }
            ?.toString()
            ?.toDouble()
            ?: versionName.substringBeforeLast('.').toDoubleOrNull()
        if (libVersion == null || libVersion !in SUPPORTED_LIB_VERSIONS) {
            logcat(LogPriority.WARN) {
                "Lib version is $libVersion, while only version(s) " +
                    "${SUPPORTED_LIB_VERSIONS.joinToString()} are supported"
            }
            return notLoaded(MangaExtension.NotLoaded.Reason.UnsupportedLibVersion, libVersion)
        }

        val signatures = getSignatures(pkgInfo)
        if (signatures.isNullOrEmpty()) {
            logcat(LogPriority.WARN) { "Package $pkgName isn't signed" }
            return notLoaded(MangaExtension.NotLoaded.Reason.Unsigned, libVersion)
        } else if (!trustExtension.isTrusted(pkgInfo, signatures)) {
            logcat(LogPriority.WARN) { "Extension $pkgName isn't trusted" }
            return notLoaded(
                MangaExtension.NotLoaded.Reason.Untrusted(signatures.last()),
                libVersion,
                signatures.last(),
            )
        }

        if (applyContentWarningsToInstalled && contentWarning !in enabledContentWarnings) {
            logcat(LogPriority.WARN) { "Extension $pkgName with $contentWarning not allowed" }
            return notLoaded(MangaExtension.NotLoaded.Reason.Filtered, libVersion)
        }

        // Everything above is cheap to check again, everything below isn't. Nothing about this apk
        // changed and it still passes, so keep the sources that are already registered for it.
        if (alreadyLoaded != null &&
            alreadyLoaded.versionCode == versionCode &&
            alreadyLoaded.isShared == extensionInfo.isShared
        ) {
            return alreadyLoaded
        }

        val classLoader = try {
            DelegateLastClassLoaderCompat(appInfo.sourceDir, null, context.classLoader)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Extension load error: $extName ($pkgName)" }
            return notLoaded(MangaExtension.NotLoaded.Reason.Failed(e.rootMessage, e.stackTraceToString()), libVersion)
        }

        val sourceClassString = metaData.getString(METADATA_SOURCE_FACTORY)
            ?: metaData.getString(METADATA_SOURCE_CLASS)
        if (sourceClassString.isNullOrBlank()) {
            logcat(LogPriority.WARN) { "Missing source class for extension $extName" }
            return notLoaded(MangaExtension.NotLoaded.Reason.Malformed, libVersion)
        }

        val sources = sourceClassString
            .split(";")
            .map {
                val sourceClass = it.trim()
                if (sourceClass.startsWith(".")) {
                    pkgInfo.packageName + sourceClass
                } else {
                    sourceClass
                }
            }
            .flatMap { className ->
                try {
                    loadSourceClass(className, classLoader)
                } catch (e: LinkageError) {
                    try {
                        val fallBackClassLoader = PathClassLoader(appInfo.sourceDir, null, context.classLoader)
                        loadSourceClass(className, fallBackClassLoader)
                    } catch (e: Throwable) {
                        logcat(LogPriority.ERROR, e) { "Extension load error: $extName ($className)" }
                        return notLoaded(
                            MangaExtension.NotLoaded.Reason.Failed(e.rootMessage, e.stackTraceToString()),
                            libVersion,
                        )
                    }
                } catch (e: Throwable) {
                    logcat(LogPriority.ERROR, e) { "Extension load error: $extName ($className)" }
                    return notLoaded(
                        MangaExtension.NotLoaded.Reason.Failed(e.rootMessage, e.stackTraceToString()),
                        libVersion,
                    )
                }
            }

        val langs = sources.filterIsInstance<CatalogueSource>()
            .map { it.lang }
            .toSet()
        val lang = when (langs.size) {
            0 -> ""
            1 -> langs.first()
            else -> "all"
        }

        return MangaExtension.Loaded(
            name = extName,
            pkgName = pkgName,
            versionName = versionName,
            versionCode = versionCode,
            libVersion = libVersion,
            lang = lang,
            contentWarning = contentWarning,
            sources = sources,
            pkgFactory = metaData.getString(METADATA_SOURCE_FACTORY),
            icon = runCatching { appInfo.loadIcon(pkgManager) }.getOrNull(),
            isShared = extensionInfo.isShared,
            // KMK -->
            signatureHash = signatures.last(),
            repoName = when {
                isKeiyoushiSigned(signatures) -> "Keiyoushi"

                else -> repos.firstOrNull { repo ->
                    signatures.all { it == repo.signingKey }
                }?.name
            },
            // KMK <--
        )
    }

    private fun isKeiyoushiSigned(signatures: List<String>): Boolean {
        return signatures.all { it == KEIYOUSHI_SIGNATURE }
    }

    /**
     * Choose which extension package to use based on version code
     *
     * @param shared extension installed to system
     * @param private extension installed to data directory
     */
    private fun selectExtensionPackage(shared: MangaExtensionInfo?, private: MangaExtensionInfo?): MangaExtensionInfo? {
        when {
            private == null && shared != null -> return shared
            shared == null && private != null -> return private
            shared == null && private == null -> return null
        }

        return if (PackageInfoCompat.getLongVersionCode(shared!!.packageInfo) >=
            PackageInfoCompat.getLongVersionCode(private!!.packageInfo)
        ) {
            shared
        } else {
            private
        }
    }

    /**
     * Returns true if the given package is an extension.
     *
     * @param pkgInfo The package info of the application.
     */
    private fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean {
        return pkgInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }
    }

    /**
     * Returns the signatures of the package or null if it's not signed.
     *
     * @param pkgInfo The package info of the application.
     * @return List SHA256 digest of the signatures
     */
    private fun getSignatures(pkgInfo: PackageInfo): List<String>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo
            when {
                signingInfo == null -> null
                signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners
                else -> signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures
        }
            ?.map { Hash.sha256(it.toByteArray()) }
            ?.toList()
    }

    /**
     * On Android 13+ the ApplicationInfo generated by getPackageArchiveInfo doesn't
     * have sourceDir which breaks assets loading (used for getting icon here).
     */
    private fun ApplicationInfo.fixBasePaths(apkPath: String) {
        if (sourceDir == null) {
            sourceDir = apkPath
        }
        if (publicSourceDir == null) {
            publicSourceDir = apkPath
        }
    }

    /**
     * Loads a source class with the given class loader
     *
     * @param name The full class name of the source to load.
     * @param classLoader The class loader to use to load the source.
     */
    private fun loadSourceClass(name: String, classLoader: ClassLoader): List<MangaSource> {
        val clazz = Class.forName(name, false, classLoader)

        val constructor = clazz.getDeclaredConstructor()
        constructor.isAccessible = true
        val instance = constructor.newInstance()

        return when (instance) {
            is MangaSource -> listOf(instance)
            is Source -> listOf(instance as MangaSource)
            is SourceFactory -> instance.createSources().filterIsInstance<MangaSource>()
            else -> throw Exception("Unknown source class type! ${instance.javaClass}")
        }
    }

    private data class MangaExtensionInfo(
        val packageInfo: PackageInfo,
        val isShared: Boolean,
    )
}

/**
 * The message of the deepest cause, which is the one that actually says what went wrong.
 */
private val Throwable.rootMessage: String
    get() {
        val root = generateSequence(this) { it.cause }.last()
        return listOfNotNull(root::class.simpleName, root.message).joinToString(": ")
    }
