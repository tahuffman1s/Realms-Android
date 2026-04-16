package com.realmsoffate.game.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.realmsoffate.game.R

/**
 * Downloadable Google Fonts for the fantasy aesthetic:
 *   - Cinzel (Roman-inscription serif, 400/700/900) for titles + section caps.
 *   - Crimson Text (book serif, 400/400italic/700) for narration prose.
 *
 * Fonts are fetched lazily by the system at first use; on first-run with no
 * network they fall back to FontFamily.Serif so rendering doesn't break.
 */
private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val cinzel = GoogleFont("Cinzel")
private val crimsonText = GoogleFont("Crimson Text")

val CinzelFontFamily: FontFamily = FontFamily(
    Font(googleFont = cinzel, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = cinzel, fontProvider = googleFontProvider, weight = FontWeight.Bold),
    Font(googleFont = cinzel, fontProvider = googleFontProvider, weight = FontWeight.Black)
)

val CrimsonTextFontFamily: FontFamily = FontFamily(
    Font(googleFont = crimsonText, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = crimsonText, fontProvider = googleFontProvider, weight = FontWeight.Normal, style = FontStyle.Italic),
    Font(googleFont = crimsonText, fontProvider = googleFontProvider, weight = FontWeight.Bold)
)
