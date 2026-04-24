package com.realmsoffate.game.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.EmojiSupportMatch
import androidx.compose.ui.text.PlatformParagraphStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Typography mirrors the web's Cinzel + Crimson Text split:
 *  - Display / headline / title: Cinzel via Google Fonts downloadable fonts
 *    (fetched at first use, cached after). Expanded letter-spacing, bold.
 *  - Body narration: Crimson Text, also via Google Fonts.
 *  - UI chrome body/labels: FontFamily.Default for tight rendering density.
 *  - `labelLarge` used as section headers — small-caps feel via letter-spacing.
 */
private val TitleSerif = CinzelFontFamily
private val BodySans = FontFamily.Default
private val NarrationSerif = CrimsonTextFontFamily

// Force every emoji to go through EmojiCompat's bundled NotoColorEmoji font so
// Samsung devices don't fall back to the OEM emoji set. EmojiCompat is inited
// in RealmsApp; without EmojiSupportMatch.All, Compose only rewrites emojis the
// system doesn't recognize — Samsung claims to support them all, so none get
// rewritten by default.
private val EmojiAllPlatformStyle = PlatformTextStyle(
    spanStyle = null,
    paragraphStyle = PlatformParagraphStyle(emojiSupportMatch = EmojiSupportMatch.All)
)

val RealmsTypography = Typography(
    displayLarge = TextStyle(fontFamily = TitleSerif, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = 2.sp, platformStyle = EmojiAllPlatformStyle),
    displayMedium = TextStyle(fontFamily = TitleSerif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = 1.5.sp, platformStyle = EmojiAllPlatformStyle),
    displaySmall = TextStyle(fontFamily = TitleSerif, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = 1.sp, platformStyle = EmojiAllPlatformStyle),
    headlineLarge = TextStyle(fontFamily = TitleSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = 1.sp, platformStyle = EmojiAllPlatformStyle),
    headlineMedium = TextStyle(fontFamily = TitleSerif, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.8.sp, platformStyle = EmojiAllPlatformStyle),
    headlineSmall = TextStyle(fontFamily = TitleSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 0.5.sp, platformStyle = EmojiAllPlatformStyle),
    titleLarge = TextStyle(fontFamily = TitleSerif, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.3.sp, platformStyle = EmojiAllPlatformStyle),
    titleMedium = TextStyle(fontFamily = TitleSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.3.sp, platformStyle = EmojiAllPlatformStyle),
    titleSmall = TextStyle(fontFamily = TitleSerif, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.4.sp, platformStyle = EmojiAllPlatformStyle),
    bodyLarge = TextStyle(fontFamily = BodySans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.2.sp, platformStyle = EmojiAllPlatformStyle),
    bodyMedium = TextStyle(fontFamily = BodySans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp, letterSpacing = 0.2.sp, platformStyle = EmojiAllPlatformStyle),
    bodySmall = TextStyle(fontFamily = BodySans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp, platformStyle = EmojiAllPlatformStyle),
    // Label styles used heavily for section headers; expanded tracking mimics Cinzel small-caps.
    labelLarge = TextStyle(fontFamily = TitleSerif, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 2.sp, platformStyle = EmojiAllPlatformStyle),
    labelMedium = TextStyle(fontFamily = BodySans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp, platformStyle = EmojiAllPlatformStyle),
    labelSmall = TextStyle(fontFamily = BodySans, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp, platformStyle = EmojiAllPlatformStyle)
)

val RealmsShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// Main narration prose stream — Crimson Text analogue.
val NarrationBodyStyle = TextStyle(
    fontFamily = NarrationSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 25.sp,
    letterSpacing = 0.2.sp,
    platformStyle = EmojiAllPlatformStyle
)

// Italic body for narration's *action* lines; used in the Markdown renderer.
val NarrationItalic = TextStyle(
    fontFamily = NarrationSerif,
    fontStyle = FontStyle.Italic,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 25.sp,
    letterSpacing = 0.2.sp,
    platformStyle = EmojiAllPlatformStyle
)
