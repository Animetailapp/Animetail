package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.manga.builtin.ehentai.EHentaiConstants
import eu.kanade.tachiyomi.ui.setting.ehentai.EhLoginScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentMap
import tachiyomi.i18n.MR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import androidx.compose.runtime.collectAsState as collectFlowAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Dedicated E-Hentai / ExHentai settings screen (Komikku-style).
 *
 * Accessible from Settings → E-Hentai (top-level entry, gated by the
 * "Enable integrated hentai features" developer toggle).
 *
 * Groups:
 *  1. Account — login / logout
 *  2. Browsing — E-Hentai vs ExHentai, improved browsing
 *  3. Default categories — bitmask category chips
 *  4. Display — title language
 *  5. Image quality — original images toggle
 *  6. Tag monitoring — watched / ignored tags
 *  7. Favorites — sync toggle
 *  8. Gallery checker — periodic update checker + interval
 */
object SettingsEHentaiScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = TLMR.strings.pref_eh_settings_title

    @Composable
    override fun getPreferences(): List<Preference> {
        val prefs = remember { Injekt.get<SourcePreferences>() }
        val trackerManager = remember { Injekt.get<TrackerManager>() }

        return listOf(
            getAccountGroup(prefs, trackerManager),
            getBrowsingGroup(prefs),
            getCategoriesGroup(prefs),
            getDisplayGroup(prefs),
            getImageQualityGroup(prefs),
            getTagMonitoringGroup(prefs),
            getFavoritesGroup(prefs),
            getGalleryCheckerGroup(prefs),
        )
    }

    // ── Account ───────────────────────────────────────────────────────────────

    @Composable
    private fun getAccountGroup(
        prefs: SourcePreferences,
        trackerManager: TrackerManager,
    ): Preference.PreferenceGroup {
        val navigator = LocalNavigator.currentOrThrow
        val eHentai = trackerManager.eHentai
        val isLoggedIn by eHentai.isLoggedInFlow.collectFlowAsState(initial = eHentai.isLoggedIn)
        var showLogoutDialog by remember { mutableStateOf(false) }

        if (showLogoutDialog) {
            EHentaiLogoutDialog(
                trackerManager = trackerManager,
                onDismiss = { showLogoutDialog = false },
            )
        }

        val accountSubtitle = if (isLoggedIn) {
            stringResource(TLMR.strings.pref_eh_account_logged_in, eHentai.getUsername())
        } else {
            stringResource(TLMR.strings.pref_eh_account_not_logged_in)
        }

        return Preference.PreferenceGroup(
            title = stringResource(TLMR.strings.pref_eh_category_account),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = if (isLoggedIn) {
                        stringResource(TLMR.strings.pref_eh_logout)
                    } else {
                        stringResource(TLMR.strings.pref_eh_login)
                    },
                    subtitle = accountSubtitle,
                    onClick = {
                        if (isLoggedIn) showLogoutDialog = true else navigator.push(EhLoginScreen())
                    },
                ),
            ),
        )
    }

    // ── Browsing ──────────────────────────────────────────────────────────────

    @Composable
    private fun getBrowsingGroup(prefs: SourcePreferences): Preference.PreferenceGroup {
        val profileEntries = (0..9).associate { i ->
            i.toString() to if (i == 0) stringResource(TLMR.strings.pref_eh_settings_profile_default) else "Profile $i"
        }.toPersistentMap()
        return Preference.PreferenceGroup(
            title = stringResource(TLMR.strings.pref_eh_category_browsing),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.ehUseExHentai(),
                    title = stringResource(TLMR.strings.pref_eh_use_exhentai),
                    subtitle = stringResource(TLMR.strings.pref_eh_use_exhentai_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.ehImprovedBrowsing(),
                    title = stringResource(TLMR.strings.pref_eh_improved_browsing),
                    subtitle = stringResource(TLMR.strings.pref_eh_improved_browsing_summary),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = prefs.ehSettingsProfile(),
                    entries = profileEntries,
                    title = stringResource(TLMR.strings.pref_eh_settings_profile),
                    subtitle = stringResource(TLMR.strings.pref_eh_settings_profile_summary),
                ),
            ),
        )
    }

    // ── Default categories ────────────────────────────────────────────────────

    @Composable
    private fun getCategoriesGroup(prefs: SourcePreferences): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(TLMR.strings.pref_eh_category_categories),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(TLMR.strings.pref_eh_default_categories),
                ) {
                    DefaultCategoriesWidget(prefs)
                },
            ),
        )
    }

    // ── Display ───────────────────────────────────────────────────────────────

    @Composable
    private fun getDisplayGroup(prefs: SourcePreferences): Preference.PreferenceGroup {
        val entries = mapOf(
            "english" to stringResource(TLMR.strings.pref_eh_title_english),
            "japanese" to stringResource(TLMR.strings.pref_eh_title_japanese),
        ).toPersistentMap()
        return Preference.PreferenceGroup(
            title = stringResource(TLMR.strings.pref_eh_category_display),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = prefs.ehTitleDisplayMode(),
                    entries = entries,
                    title = stringResource(TLMR.strings.pref_eh_title_display_mode),
                ),
            ),
        )
    }

    // ── Image quality ─────────────────────────────────────────────────────────

    @Composable
    private fun getImageQualityGroup(prefs: SourcePreferences): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(TLMR.strings.pref_eh_category_image_quality),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.ehOriginalImages(),
                    title = stringResource(TLMR.strings.pref_eh_original_images),
                    subtitle = stringResource(TLMR.strings.pref_eh_original_images_summary),
                ),
            ),
        )
    }

    // ── Tag monitoring ────────────────────────────────────────────────────────

    @Composable
    private fun getTagMonitoringGroup(prefs: SourcePreferences): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(TLMR.strings.pref_eh_category_tag_monitoring),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(TLMR.strings.pref_eh_watched_tags),
                ) {
                    TagSetWidget(
                        tagsPref = prefs.ehWatchedTags(),
                        subtitle = stringResource(TLMR.strings.pref_eh_watched_tags_summary),
                        addHint = stringResource(TLMR.strings.pref_eh_add_tag_hint),
                    )
                },
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(TLMR.strings.pref_eh_ignored_tags),
                ) {
                    TagSetWidget(
                        tagsPref = prefs.ehIgnoredTags(),
                        subtitle = stringResource(TLMR.strings.pref_eh_ignored_tags_summary),
                        addHint = stringResource(TLMR.strings.pref_eh_add_tag_hint),
                    )
                },
            ),
        )
    }

    // ── Favorites sync ────────────────────────────────────────────────────────

    @Composable
    private fun getFavoritesGroup(prefs: SourcePreferences): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(TLMR.strings.pref_eh_category_favorites),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.ehFavoritesSync(),
                    title = stringResource(TLMR.strings.pref_eh_favorites_sync),
                    subtitle = stringResource(TLMR.strings.pref_eh_favorites_sync_summary),
                ),
            ),
        )
    }

    // ── Gallery checker ───────────────────────────────────────────────────────

    @Composable
    private fun getGalleryCheckerGroup(prefs: SourcePreferences): Preference.PreferenceGroup {
        val checkerEnabled by prefs.ehGalleryChecker().collectAsState()
        val intervalEntries = mapOf(
            6 to stringResource(TLMR.strings.pref_eh_interval_6h),
            12 to stringResource(TLMR.strings.pref_eh_interval_12h),
            24 to stringResource(TLMR.strings.pref_eh_interval_24h),
            48 to stringResource(TLMR.strings.pref_eh_interval_48h),
        ).toPersistentMap()

        return Preference.PreferenceGroup(
            title = stringResource(TLMR.strings.pref_eh_category_gallery_checker),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.ehGalleryChecker(),
                    title = stringResource(TLMR.strings.pref_eh_gallery_checker),
                    subtitle = stringResource(TLMR.strings.pref_eh_gallery_checker_summary),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = prefs.ehGalleryCheckerInterval(),
                    entries = intervalEntries,
                    title = stringResource(TLMR.strings.pref_eh_gallery_checker_interval),
                    enabled = checkerEnabled,
                ),
            ),
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Custom composable widgets
    // ═══════════════════════════════════════════════════════════════════════════

    /** Category chips — filled = included, outlined = excluded. */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun DefaultCategoriesWidget(prefs: SourcePreferences) {
        var excludedMask by remember {
            mutableStateOf(prefs.ehDefaultCategories().get())
        }

        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
            Text(
                text = stringResource(TLMR.strings.pref_eh_default_categories_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EHentaiConstants.CATEGORY_NAMES.forEach { (bitmask, name) ->
                    val included = (excludedMask and bitmask) == 0
                    FilterChip(
                        selected = included,
                        onClick = {
                            excludedMask = if (included) {
                                excludedMask or bitmask
                            } else {
                                excludedMask and bitmask.inv()
                            }
                            prefs.ehDefaultCategories().set(excludedMask)
                        },
                        label = { Text(name) },
                    )
                }
            }
        }
    }

    /** Tag-set editor: list of current tags (with remove) + "Add tag" button. */
    @Composable
    private fun TagSetWidget(
        tagsPref: tachiyomi.core.common.preference.Preference<Set<String>>,
        subtitle: String,
        addHint: String,
    ) {
        val tags by tagsPref.collectAsState()
        var showAddDialog by remember { mutableStateOf(false) }

        if (showAddDialog) {
            AddTagDialog(
                hint = addHint,
                onDismiss = { showAddDialog = false },
                onConfirm = { newTag ->
                    if (newTag.isNotBlank()) tagsPref.set(tags + newTag.trim())
                    showAddDialog = false
                },
            )
        }

        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, bottom = 4.dp)) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            tags.forEach { tag ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { tagsPref.set(tags - tag) }) {
                        Icon(
                            imageVector = Icons.Outlined.RemoveCircleOutline,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            TextButton(onClick = { showAddDialog = true }) {
                Text(addHint)
            }
        }
    }

    @Composable
    private fun AddTagDialog(
        hint: String,
        onDismiss: () -> Unit,
        onConfirm: (String) -> Unit,
    ) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(hint) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    placeholder = { Text("e.g. artist:foo") },
                )
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(text) }) {
                    Text(stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Logout dialog
    // ═══════════════════════════════════════════════════════════════════════════

    @Composable
    private fun EHentaiLogoutDialog(
        trackerManager: TrackerManager,
        onDismiss: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(TLMR.strings.pref_eh_logout)) },
            text = { Text(stringResource(TLMR.strings.pref_eh_logout_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    trackerManager.eHentai.logout()
                    onDismiss()
                }) {
                    Text(stringResource(TLMR.strings.pref_eh_logout))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
}
