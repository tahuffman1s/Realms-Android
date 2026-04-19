package com.realmsoffate.game.ui.overlays

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realmsoffate.game.ui.theme.RealmsSpacing

/**
 * Target-prompt spec: carried by the VM so the Compose tree can render the
 * dialog wherever is convenient. `verb` is the lead-in ("I attack",
 * "I cast Fire Bolt at"); on submit we join verb + target (or keep verb alone
 * if the player left the text blank).
 */
data class TargetPromptSpec(
    val title: String,
    val verb: String,
    val selfCastable: Boolean = false,
    /** Recently-met NPC names for quick-pick chips. */
    val recentTargets: List<String> = emptyList()
)

/**
 * Bottom-sheet target-picker. "Self" quick-cast if the source allows it;
 * tap a recent-NPC chip to prefill, edit freely, then Submit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetPromptDialog(
    spec: TargetPromptSpec,
    onSubmit: (String) -> Unit,
    onSelf: () -> Unit,
    onDismiss: () -> Unit
) {
    var target by remember(spec) { mutableStateOf("") }
    val composed = remember(spec, target) {
        if (target.isBlank()) spec.verb else "${spec.verb.trimEnd()} ${target.trim()}"
    }

    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(Modifier.padding(horizontal = RealmsSpacing.xl, vertical = RealmsSpacing.s)) {
            Text(
                spec.title.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Target (leave blank for narrator's pick)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(RealmsSpacing.s))
            OutlinedTextField(
                value = target,
                onValueChange = { target = it.take(60) },
                placeholder = { Text("the goblin · the merchant · the door…", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            )
            if (spec.recentTargets.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    spec.recentTargets.take(8).forEach { name ->
                        AssistChip(
                            onClick = { target = name },
                            label = { Text(name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // Preview
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().border(
                    1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small
                )
            ) {
                Text(
                    "\u201C$composed.\u201D",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Spacer(Modifier.height(RealmsSpacing.m))
            Row(horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.s)) {
                if (spec.selfCastable) {
                    OutlinedButton(
                        onClick = { onSelf() },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small
                    ) { Text("On self", fontWeight = FontWeight.Bold) }
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small
                ) { Text("Cancel") }
                Button(
                    onClick = { onSubmit(composed) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = MaterialTheme.shapes.small
                ) { Text("Submit", fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.navigationBarsPadding().height(14.dp))
        }
    }
}
