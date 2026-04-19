package com.realmsoffate.game.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realmsoffate.game.game.GameViewModel
import com.realmsoffate.game.ui.theme.RealmsSpacing

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

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = RealmsSpacing.xxl)
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

            // DeepSeek provider header
            Spacer(Modifier.height(14.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(RealmsSpacing.m)) {
                    Text(
                        "DEEPSEEK",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Nearly free · \$0.28/M tokens",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "platform.deepseek.com/api_keys",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionLabel("\uD83D\uDD11  API KEY")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = localKey,
                onValueChange = { localKey = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(provider.placeholder, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.secondary) },
                trailingIcon = {
                    if (valid) Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary)
                },
                supportingText = { Text("DeepSeek keys start with sk-") },
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            )

            Spacer(Modifier.height(16.dp))

            StartButton(valid = valid, onClick = { vm.setApiKey(localKey); vm.confirmApiKey() })

            Spacer(Modifier.height(14.dp))
            Text(
                "Your key stays on this device via EncryptedDataStore. Never sent anywhere except the provider you pick.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun StartButton(valid: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = valid,
        shape = MaterialTheme.shapes.medium,
        color = if (valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (valid) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Box(
            modifier = if (valid) Modifier.background(
                Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))
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

