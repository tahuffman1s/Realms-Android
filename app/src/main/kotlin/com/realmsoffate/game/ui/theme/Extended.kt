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
    val systemOnBubble: Color,
    val scrimOverlay: Color,
    val npcPalette: List<Pair<Color, Color>>
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
    systemOnBubble = Color(0xFF9E9AA8),
    scrimOverlay = Color(0xB3000000),
    npcPalette = listOf(
        Color(0xFF4A9E5E) to Color(0xFF1B3D23),
        Color(0xFF5B7FC7) to Color(0xFF1C2D4A),
        Color(0xFFD4A843) to Color(0xFF3D3118),
        Color(0xFFC44040) to Color(0xFF3D1818),
        Color(0xFF8B6CC7) to Color(0xFF2D1F42),
        Color(0xFF4AA8A8) to Color(0xFF1A3636),
        Color(0xFFCC6633) to Color(0xFF3D2010),
        Color(0xFFAA44AA) to Color(0xFF361836),
        Color(0xFF6A9E3A) to Color(0xFF223312),
        Color(0xFF5577CC) to Color(0xFF1A2540),
    )
)

private val LightExtended = RealmsExtendedColors(
    critGold = Color(0xFF7A5C10),
    critGlow = Color(0x337A5C10),
    fumbleRed = Color(0xFFC72B1E),
    fumbleGlow = Color(0x33C72B1E),
    success = Color(0xFF1E6E34),
    warning = Color(0xFFC47A00),
    info = Color(0xFF1260CC),
    goldAccent = Color(0xFF7A5C10),
    playerDot = Color(0xFF1260CC),
    sceneDivider = Color(0x33000000),
    rarityCommon = Color(0xFF5A5570),
    rarityUncommon = Color(0xFF1E6E34),
    rarityRare = Color(0xFF1260CC),
    rarityEpic = Color(0xFF7B3DAA),
    rarityLegendary = Color(0xFF7A5C10),
    narratorBubble = Color(0xFFF0EDE6),
    narratorOnBubble = Color(0xFF1C1B1F),
    npcBubble = Color(0xFFE3ECF5),
    npcOnBubble = Color(0xFF1A2533),
    playerBubble = Color(0xFFEADDFF),
    playerOnBubble = Color(0xFF21005D),
    asideBubble = Color(0xFFE8E1F0),
    asideOnBubble = Color(0xFF6750A4),
    systemBubble = Color(0x1A000000),
    systemOnBubble = Color(0xFF49454F),
    scrimOverlay = Color(0xB3000000),
    npcPalette = listOf(
        Color(0xFF2D6B3A) to Color(0xFFDFF0E3),
        Color(0xFF3A5A9E) to Color(0xFFDDE6F5),
        Color(0xFF8A6E1A) to Color(0xFFF5EDD0),
        Color(0xFF9E2020) to Color(0xFFF5D8D8),
        Color(0xFF6A4A9E) to Color(0xFFEADFF5),
        Color(0xFF2D7A7A) to Color(0xFFD8F0F0),
        Color(0xFF9E4A1A) to Color(0xFFF5E0D0),
        Color(0xFF8A2D8A) to Color(0xFFF0D8F0),
        Color(0xFF4A7A20) to Color(0xFFE0EDCF),
        Color(0xFF3A5599) to Color(0xFFD8E0F5),
    )
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
