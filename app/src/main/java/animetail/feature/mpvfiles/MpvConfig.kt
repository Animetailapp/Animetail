package animetail.feature.mpvfiles

import android.content.Context
import android.system.Os
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.ui.player.settings.AdvancedPlayerPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.custombuttons.interactor.GetCustomButtons
import tachiyomi.domain.custombuttons.model.CustomButton
import tachiyomi.domain.storage.service.StorageManager
import java.io.File
import java.io.IOException

@Inject
class MpvConfig(
    private val context: Context,
    private val storageManager: StorageManager,
    private val advancedPlayerPreferences: AdvancedPlayerPreferences,
    private val getCustomButtons: GetCustomButtons,
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var copyJob: Job? = null

    fun copyFiles() {
        if (copyJob?.isActive == true) return

        copyJob = scope.launchIO {
            val mpvDir = getMpvDir()
            copyUserFiles(mpvDir)
            copyFontsDirectory(mpvDir)
            writeFontsConf(mpvDir)
        }
    }

    private fun getMpvDir(): UniFile {
        return UniFile.fromFile(context.filesDir)!!.createDirectory(MPV_DIR)!!
    }

    private suspend fun copyUserFiles(mpvDir: UniFile) {
        // First, delete all present scripts
        val scriptsDir = { mpvDir.createDirectory(MPV_SCRIPTS_DIR) }
        val scriptOptsDir = { mpvDir.createDirectory(MPV_SCRIPTS_OPTS_DIR) }
        val shadersDir = { mpvDir.createDirectory(MPV_SHADERS_DIR) }

        scriptsDir()?.delete()
        scriptOptsDir()?.delete()
        shadersDir()?.delete()

        // Then, copy the user files from the Aniyomi directory
        if (advancedPlayerPreferences.mpvUserFiles().get()) {
            storageManager.getScriptsDirectory()?.listFiles()?.forEach { file ->
                ensureActive()
                val outFile = scriptsDir()?.createFile(file.name)
                outFile?.let {
                    file.openInputStream().copyTo(it.openOutputStream())
                }
            }
            storageManager.getScriptOptsDirectory()?.listFiles()?.forEach { file ->
                ensureActive()
                val outFile = scriptOptsDir()?.createFile(file.name)
                outFile?.let {
                    file.openInputStream().copyTo(it.openOutputStream())
                }
            }
            storageManager.getShadersDirectory()?.listFiles()?.forEach { file ->
                ensureActive()
                val outFile = shadersDir()?.createFile(file.name)
                outFile?.let {
                    file.openInputStream().copyTo(it.openOutputStream())
                }
            }
        }

        val buttons = getCustomButtons.getAll()
        setupCustomButtons(buttons)

        // Copy over the bridge file
        val luaFile = scriptsDir()?.createFile("aniyomi.lua")
        val luaBridge = context.assets.open("aniyomi.lua")
        luaFile?.openOutputStream()?.bufferedWriter()?.use { scriptLua ->
            luaBridge.bufferedReader().use { scriptLua.write(it.readText()) }
        }
    }

    fun setupCustomButtons(buttons: List<CustomButton>) {
        val scriptsDir = getMpvDir().createDirectory(MPV_SCRIPTS_DIR)!!
        val primaryButtonId = buttons.firstOrNull { it.isFavorite }?.id ?: 0L

        val customButtonsContent = buildString {
            appendLine(
                """
                local lua_modules = mp.find_config_file('scripts')
                if lua_modules then
                    package.path = package.path .. ';' .. lua_modules .. '/?.lua;' .. lua_modules .. '/?/init.lua;' .. '${scriptsDir.filePath}' .. '/?.lua'
                end
                local aniyomi = require 'aniyomi'
                """.trimIndent(),
            )

            buttons.forEach { button ->
                appendLine(
                    """
                    ${button.getButtonOnStartup(primaryButtonId)}
                    function button${button.id}()
                        ${button.getButtonContent(primaryButtonId)}
                    end
                    mp.register_script_message('call_button_${button.id}', button${button.id})
                    function button${button.id}long()
                        ${button.getButtonLongPressContent(primaryButtonId)}
                    end
                    mp.register_script_message('call_button_${button.id}_long', button${button.id}long)
                    """.trimIndent(),
                )
            }
        }

        val file = scriptsDir.createFile("custombuttons.lua")
        file?.openOutputStream()?.bufferedWriter()?.use {
            it.write(customButtonsContent)
        }
    }

    private suspend fun copyFontsDirectory(mpvDir: UniFile) {
        // TODO: I think this is a bad hack.
        // We need to find a way to let MPV directly access our fonts directory.
        val fontsDirectory = mpvDir.createDirectory(MPV_FONTS_DIR)!!

        storageManager.getFontsDirectory()?.listFiles()?.forEach { font ->
            ensureActive()
            val outFile = fontsDirectory.createFile(font.name)
            outFile?.let {
                font.openInputStream().copyTo(it.openOutputStream())
            }
        }
    }

    private fun writeFontsConf(mpvDir: UniFile) {
        setupFontsConf(context, mpvDir)
    }

    private suspend inline fun ensureActive() {
        if (!currentCoroutineContext().isActive) {
            throw CancellationException()
        }
    }

    companion object {
        const val MPV_DIR = "mpv"
        const val MPV_FONTS_DIR = "fonts"
        const val MPV_SCRIPTS_DIR = "scripts"
        const val MPV_SCRIPTS_OPTS_DIR = "script-opts"
        const val MPV_SHADERS_DIR = "shaders"

        private val ROBOTO_WEIGHT_STYLES = listOf(
            "Thin" to listOf("Thin", "Thin Italic"),
            "Light" to listOf("Light", "Light Italic"),
            "Regular" to listOf("Regular", "Italic"),
            "Medium" to listOf("Medium", "Medium Italic"),
            "Bold" to listOf("Bold", "Bold Italic"),
            "Black" to listOf("Black", "Black Italic"),
        )

        fun setupFontsConf(context: Context, mpvDir: UniFile? = null) {
            val resolvedMpvDir = mpvDir
                ?: UniFile.fromFile(context.filesDir)!!.createDirectory(MPV_DIR)!!
            val fontsDirectory = resolvedMpvDir.createDirectory(MPV_FONTS_DIR)!!.filePath!!
            val fontconfigCacheDir = File(context.cacheDir, "fontconfig").apply { mkdirs() }.path
            val content = buildFontsConfXml(fontsDirectory, fontconfigCacheDir)

            try {
                val fontsConfFile = resolvedMpvDir.createFile("fonts.conf")
                fontsConfFile?.openOutputStream()?.bufferedWriter()?.use {
                    it.write(content)
                }
                val mpvDirPath = resolvedMpvDir.filePath ?: File(context.filesDir, MPV_DIR).absolutePath
                val fontsConfPath = fontsConfFile?.filePath ?: File(mpvDirPath, "fonts.conf").absolutePath
                runCatching {
                    Os.setenv("FONTCONFIG_FILE", fontsConfPath, true)
                    Os.setenv("FONTCONFIG_PATH", mpvDirPath, true)
                }
            } catch (e: IOException) {
                logcat(LogPriority.ERROR, e) { "Failed to write fonts.conf" }
            }
        }

        private fun buildFontsConfXml(fontsDirectory: String, cacheDirectory: String): String = buildString {
            appendLine("""<?xml version="1.0"?>""")
            appendLine("""<!DOCTYPE fontconfig SYSTEM "urn:fontconfig:fonts.dtd">""")
            appendLine("<fontconfig>")
            appendLine("<dir>/system/fonts/</dir>")
            appendLine("<dir>/product/fonts/</dir>")
            appendLine("<dir>$fontsDirectory</dir>")
            appendLine("<cachedir>$cacheDirectory</cachedir>")
            appendLine("<alias><family>serif</family><prefer><family>Noto Serif</family></prefer></alias>")
            appendLine(
                "<alias><family>sans-serif</family>" +
                    "<prefer><family>Roboto</family><family>Noto Sans</family></prefer></alias>",
            )
            appendLine(
                "<alias><family>Sans Serif</family>" +
                    "<prefer><family>Roboto</family><family>Noto Sans</family></prefer></alias>",
            )
            appendLine(
                "<alias><family>monospace</family><prefer><family>Droid Sans Mono</family></prefer></alias>",
            )

            // Ensure scanned fonts include their full name and PostScript name in FC_FAMILY so libass
            // evaluates weight/italic similarity via matches_family_name() instead of short-circuiting
            // on upright faces in matches_full_or_postscript_name().
            appendLine("<match target=\"scan\">")
            appendLine("  <edit name=\"family\" mode=\"append\"><name>fullname</name></edit>")
            appendLine("  <edit name=\"family\" mode=\"append\"><name>postscriptname</name></edit>")
            appendLine("</match>")

            // Map both upright and italic Roboto weight styles to "Roboto <Weight>" and "Roboto-<Weight>"
            // families so libass selects dedicated italic faces (e.g. Roboto-MediumItalic) instead of
            // synthetically slanting the upright face.
            for ((weight, styles) in ROBOTO_WEIGHT_STYLES) {
                val spaceFamily = "Roboto $weight"
                val hyphenFamily = "Roboto-$weight"
                for (style in styles) {
                    appendLine("<match target=\"scan\">")
                    appendLine("  <test name=\"family\" compare=\"eq\"><string>Roboto</string></test>")
                    appendLine("  <test name=\"style\" compare=\"eq\"><string>$style</string></test>")
                    appendLine("  <edit name=\"family\" mode=\"append\"><string>$spaceFamily</string></edit>")
                    appendLine("  <edit name=\"family\" mode=\"append\"><string>$hyphenFamily</string></edit>")
                    appendLine("</match>")
                }
                appendLine("<match target=\"scan\">")
                appendLine("  <test name=\"family\" compare=\"eq\"><string>$spaceFamily</string></test>")
                appendLine("  <edit name=\"family\" mode=\"append\"><string>$hyphenFamily</string></edit>")
                appendLine("</match>")
                appendLine("<match target=\"scan\">")
                appendLine("  <test name=\"family\" compare=\"eq\"><string>$hyphenFamily</string></test>")
                appendLine("  <edit name=\"family\" mode=\"append\"><string>$spaceFamily</string></edit>")
                appendLine("</match>")
                appendLine("<match target=\"pattern\">")
                appendLine("  <test name=\"family\" compare=\"eq\"><string>$hyphenFamily</string></test>")
                appendLine("  <edit name=\"family\" mode=\"prepend\"><string>$spaceFamily</string></edit>")
                appendLine("</match>")
            }

            append("</fontconfig>")
        }
    }
}
