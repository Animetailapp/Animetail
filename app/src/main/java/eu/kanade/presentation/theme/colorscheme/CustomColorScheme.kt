package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.ktx.DynamicScheme
import com.materialkolor.toColorScheme
import eu.kanade.domain.ui.UiPreferences

internal class CustomColorScheme(uiPreferences: UiPreferences) : BaseColorScheme() {

    private val seed = uiPreferences.colorTheme.get()
    private val style = uiPreferences.customThemeStyle.get()

    private val custom = CustomCompatColorScheme(seed, style)

    override val darkScheme
        get() = custom.darkScheme

    override val lightScheme
        get() = custom.lightScheme
}

internal class CustomCompatColorScheme(seed: Int, style: PaletteStyle = PaletteStyle.Fidelity) : BaseColorScheme() {

    override val lightScheme = generateColorSchemeFromSeed(seed = Color(seed), dark = false, style = style)
    override val darkScheme = generateColorSchemeFromSeed(seed = Color(seed), dark = true, style = style)

    companion object {
        fun generateColorSchemeFromSeed(
            seed: Color,
            dark: Boolean,
            style: PaletteStyle = PaletteStyle.Fidelity,
        ): ColorScheme {
            return DynamicScheme(
                seedColor = seed,
                isDark = dark,
                specVersion = ColorSpec.SpecVersion.SPEC_2025,
                style = style,
            )
                .toColorScheme(isAmoled = false)
        }
    }
}
