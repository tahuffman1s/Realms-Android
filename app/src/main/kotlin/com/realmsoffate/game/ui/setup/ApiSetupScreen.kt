package com.realmsoffate.game.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realmsoffate.game.data.AiProvider
import com.realmsoffate.game.game.GameViewModel
import com.realmsoffate.game.ui.theme.RealmsTheme

/**
 * API setup screen — mirrors the web's provider picker pattern:
 * three colored provider tiles, monospace key input, large gradient
 * "START GAME" affordance when valid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiSetupScreen(vm: GameViewModel) {
    val provider by vm.provider.collectAsState()
    val apiKey by vm.apiKey.collectAsState()
    var localKey by rememberSaveable { mutableStateOf(apiKey) }
    LaunchedEffect(apiKey) { if (localKey.isEmpty() && apiKey.isNotEmpty()) localKey = apiKey }

    val valid = provider.validate(localKey)
    val realms = RealmsTheme.colors

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 22.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(56.dp))
            Text("\u2694\uFE0F", fontSize = 72.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "REALMS",
                style = MaterialTheme.typography.displayMedium.copy(letterSpacing = 8.sp),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "A D&D 5e narrative",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 3.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(36.dp))

            // Provider tiles
            SectionLabel("\uD83E\uDD16  AI PROVIDER")
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AiProvider.values().forEach { p ->
                    ProviderTile(
                        provider = p,
                        selected = p == provider,
                        onClick = { vm.setProvider(p) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            // Info blurb
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        providerHeadline(provider),
                        style = MaterialTheme.typography.titleSmall,
                        color = providerColor(provider, realms)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        providerBody(provider),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        providerLink(provider),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                        color = realms.goldAccent
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionLabel("\uD83D\uDD11  API KEY")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = localKey,
                onValueChange = { localKey = it; vm.setApiKey(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(provider.placeholder, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Key, null, tint = providerColor(provider, realms)) },
                trailingIcon = {
                    if (valid) Icon(Icons.Default.Lock, null, tint = realms.success)
                },
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            )

            Spacer(Modifier.height(16.dp))

            StartButton(valid = valid, onClick = { vm.confirmApiKey() })

            Spacer(Modifier.height(14.dp))
            Text(
                "Your key stays on this device via EncryptedDataStore. Never sent anywhere except the provider you pick.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ProviderTile(
    provider: AiProvider,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val realms = RealmsTheme.colors
    val color = providerColor(provider, realms)
    val bg = if (selected) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val borderColor = if (selected) color else MaterialTheme.colorScheme.outlineVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp)
    ) {
        Text(
            provider.label.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            providerTagline(provider),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StartButton(valid: Boolean, onClick: () -> Unit) {
    val realms = RealmsTheme.colors
    Surface(
        onClick = onClick,
        enabled = valid,
        shape = RoundedCornerShape(16.dp),
        color = if (valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (valid) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Box(
            modifier = if (valid) Modifier.background(
                Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, realms.goldAccent))
            ) else Modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (valid) "BEGIN THE TALE" else "ENTER A VALID KEY",
                style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 3.sp),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun providerColor(p: AiProvider, realms: com.realmsoffate.game.ui.theme.RealmsExtendedColors): Color = when (p) {
    AiProvider.GEMINI -> Color(0xFF4285F4)
    AiProvider.DEEPSEEK -> Color(0xFFB197FF)
    AiProvider.CLAUDE -> realms.goldAccent
}

private fun providerTagline(p: AiProvider): String = when (p) {
    AiProvider.GEMINI -> "Free\n250/day"
    AiProvider.DEEPSEEK -> "~Free\n$0.28/M"
    AiProvider.CLAUDE -> "Premium\nSonnet 4"
}

private fun providerHeadline(p: AiProvider): String = when (p) {
    AiProvider.GEMINI -> "100% FREE tier"
    AiProvider.DEEPSEEK -> "Nearly free, highly tuned"
    AiProvider.CLAUDE -> "Best narrative quality"
}

private fun providerBody(p: AiProvider): String = when (p) {
    AiProvider.GEMINI -> "250 requests/day, no credit card. Gemini 2.5 Flash — fast, coherent."
    AiProvider.DEEPSEEK -> "5M free tokens (~500 turns), then $0.28/M. Web-port sampling (temp 0.9, top_p 0.95, cache-stable prefix)."
    AiProvider.CLAUDE -> "$5 free credit (~80 turns). Claude Sonnet — richest world consistency, strongest NPCs."
}

private fun providerLink(p: AiProvider): String = when (p) {
    AiProvider.GEMINI -> "aistudio.google.com/apikey"
    AiProvider.DEEPSEEK -> "platform.deepseek.com/api_keys"
    AiProvider.CLAUDE -> "console.anthropic.com"
}
