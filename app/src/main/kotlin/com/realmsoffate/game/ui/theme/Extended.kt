package com.realmsoffate.game.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Fantasy-flavoured accent tokens that sit on top of the Material 3 ColorScheme.
 * Dynamic color (Material You) drives primary/secondary/tertiary; these provide
 * the crit-gold / fumble-red / success-green / scene-divider etc. that the web
 * version relies on for atmospheric contrast.
 */
data class RealmsExtendedColors(
    val critGold: Color,
    val critGlow: Color,
    val fumbleRed: Color,
    val fumbleGlow: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
    val goldAccent: Color,
    val playerDot: Color,
    val sceneDivider: Color,
    val rarityCommon: Color,
    val rarityUncommon: Color,
    val rarityRare: Color,
    val rarityEpic: Color,
    val rarityLegendary: Color,
    val narratorBubble: Color,
    val narratorOnBubble: Color,
    val npcBubble: Color,
    val npcOnBubble: Color,
    val playerBubble: Color,
    val playerOnBubble: Color,
    val asideBubble: Color,
    val asideOnBubble: Color,
    val systemBubble: Color,
    val systemOnBubble: Color
)

private val DarkExtended = RealmsExtendedColors(
    critGold = Color(0xFFFFD76A),
    critGlow = Color(0x55FFD76A),
    fumbleRed = Color(0xFFFF6B5C),
    fumbleGlow = Color(0x55FF6B5C),
    success = Color(0xFF7DD892),
    warning = Color(0xFFFFB86B),
    info = Color(0xFF82B6E8),
    goldAccent = Color(0xFFE2B84A),
    playerDot = Color(0xFF82B6E8),
    sceneDivider = Color(0x33FFFFFF),
    rarityCommon = Color(0xFFB8B3C2),
    rarityUncommon = Color(0xFF7DD892),
    rarityRare = Color(0xFF82B6E8),
    rarityEpic = Color(0xFFC79DFF),
    rarityLegendary = Color(0xFFFFD76A),
    narratorBubble = Color(0xFF2A262F),
    narratorOnBubble = Color(0xFFE8E1F0),
    npcBubble = Color(0xFF1E2A3A),
    npcOnBubble = Color(0xFFD0E0F0),
    playerBubble = Color(0xFF3A2C55),
    playerOnBubble = Color(0xFFE4D7FF),
    asideBubble = Color(0xFF1A1030),
    asideOnBubble = Color(0xFFB197FF),
    systemBubble = Color(0x1AFFFFFF),
    systemOnBubble = Color(0xFF9E9AA8)
)

private val LightExtended = RealmsExtendedColors(
    critGold = Color(0xFFB8891A),
    critGlow = Color(0x33B8891A),
    fumbleRed = Color(0xFFC72B1E),
    fumbleGlow = Color(0x33C72B1E),
    success = Color(0xFF2E7D42),
    warning = Color(0xFFC47A00),
    info = Color(0xFF1A73E8),
    goldAccent = Color(0xFFB8891A),
    playerDot = Color(0xFF1A73E8),
    sceneDivider = Color(0x33000000),
    rarityCommon = Color(0xFF5A5570),
    rarityUncommon = Color(0xFF2E7D42),
    rarityRare = Color(0xFF1A73E8),
    rarityEpic = Color(0xFF7B3DAA),
    rarityLegendary = Color(0xFFB8891A),
    narratorBubble = Color(0xFFF0EDE6),
    narratorOnBubble = Color(0xFF1C1B1F),
    npcBubble = Color(0xFFE3ECF5),
    npcOnBubble = Color(0xFF1A2533),
    playerBubble = Color(0xFFEADDFF),
    playerOnBubble = Color(0xFF21005D),
    asideBubble = Color(0xFFE8E1F0),
    asideOnBubble = Color(0xFF6750A4),
    systemBubble = Color(0x1A000000),
    systemOnBubble = Color(0xFF49454F)
)

val LocalRealmsColors = staticCompositionLocalOf { DarkExtended }

fun extendedColorsFor(dark: Boolean): RealmsExtendedColors =
    if (dark) DarkExtended else LightExtended

object RealmsTheme {
    val colors: RealmsExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalRealmsColors.current
}
