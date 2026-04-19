package com.realmsoffate.game.ui.panels

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.ui.components.PanelTab
import com.realmsoffate.game.ui.components.PanelSheet
import com.realmsoffate.game.ui.components.PanelTabRow
import com.realmsoffate.game.ui.components.RealmsCard
import com.realmsoffate.game.ui.components.SectionHeader
import com.realmsoffate.game.ui.components.StatusTag
import com.realmsoffate.game.ui.components.WealthBars
import com.realmsoffate.game.ui.theme.RealmsSpacing
import com.realmsoffate.game.util.formatSigned

// ----------------- CURRENCY (exchange + rate table) -----------------

@Composable
internal fun CurrencyContent(
    state: GameUiState,
    onExchange: (factionName: String, direction: String, amount: Int) -> Unit = { _, _, _ -> }
) {
    val ch = state.character ?: return
    val factions = state.worldLore?.factions.orEmpty()
    val localFactionName = state.worldLore?.let { lore ->
        state.worldMap?.let { wm ->
            com.realmsoffate.game.game.LoreGen.findLocalFaction(wm, lore, state.currentLoc)?.name
        }
    }
    var exchangeTarget by remember(ch) { mutableStateOf<String?>(null) }

    // Total wealth estimate in gold equivalent.
    val wealthGold = ch.gold + ch.currencyBalances.entries.sumOf { (currency, amt) ->
        val faction = factions.firstOrNull { it.currency == currency }
        val w = faction?.economy?.wealth ?: 3
        val rate = (0.6 + 0.2 * (w - 3)).coerceIn(0.3, 1.6)
        (amt / rate).toInt()
    }

    LazyColumn(
        Modifier.padding(horizontal = RealmsSpacing.m).heightIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(RealmsSpacing.s)
    ) {
        // Wealth header card.
        item {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(RealmsSpacing.l)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("\uD83D\uDCB0", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.width(RealmsSpacing.m))
                        Column {
                            Text("GOLD ON HAND", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                            Text(
                                "${ch.gold}",
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    Spacer(Modifier.height(RealmsSpacing.xs))
                    Text(
                        "Total wealth (gold-eq): $wealthGold",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Per-faction currency cards with exchange affordance.
        item { SectionHeader("LOCAL CURRENCIES") }
        items(factions) { f ->
            val balance = ch.currencyBalances[f.currency] ?: 0
            val wealth = f.economy?.wealth ?: 3
            val rate = (0.6 + 0.2 * (wealth - 3)).coerceIn(0.3, 1.6)
            val rep = state.factionRep[f.name] ?: 0
            CurrencyFactionCard(
                name = f.name,
                currency = f.currency,
                balance = balance,
                wealth = wealth,
                rate = rate,
                repColor = when {
                    rep >= 50 -> MaterialTheme.colorScheme.primary
                    rep <= -50 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                rep = rep,
                isLocal = (f.name == localFactionName),
                onExchange = { exchangeTarget = f.name }
            )
        }

        // Exchange UI for the chosen faction.
        exchangeTarget?.let { t ->
            val f = factions.firstOrNull { it.name == t }
            if (f != null) {
                item {
                    ExchangeCard(
                        faction = f,
                        character = ch,
                        onCommit = { direction, amount ->
                            onExchange(t, direction, amount)
                            exchangeTarget = null
                        },
                        onCancel = { exchangeTarget = null }
                    )
                }
            }
        }

        // Rate table at the bottom.
        item {
            SectionHeader("EXCHANGE RATES")
            RatesTable(factions)
            Spacer(Modifier.height(RealmsSpacing.xxs))
            Text(
                "Rates follow economy strength: richer markets = more favourable swaps. Shops trade in local currency; higher reputation means better prices.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun CurrencyPanel(
    state: GameUiState,
    onClose: () -> Unit,
    onExchange: (factionName: String, direction: String, amount: Int) -> Unit = { _, _, _ -> }
) {
    val localFactionName = state.worldLore?.let { lore ->
        state.worldMap?.let { wm ->
            com.realmsoffate.game.game.LoreGen.findLocalFaction(wm, lore, state.currentLoc)?.name
        }
    }
    PanelSheet(
        "\uD83D\uDCB0  Coin",
        subtitle = localFactionName?.let { "Local: $it" },
        onClose = onClose
    ) {
        CurrencyContent(state, onExchange)
    }
}

@Composable
private fun CurrencyFactionCard(
    name: String,
    currency: String,
    balance: Int,
    wealth: Int,
    rate: Double,
    repColor: Color,
    rep: Int,
    isLocal: Boolean,
    onExchange: () -> Unit
) {
    RealmsCard(
        modifier = Modifier.fillMaxWidth().border(
            1.dp,
            if (isLocal) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant,
            MaterialTheme.shapes.medium
        ),
        shape = MaterialTheme.shapes.medium,
        contentPadding = RealmsSpacing.m
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (isLocal) {
                        Spacer(Modifier.width(RealmsSpacing.xs))
                        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                            Text(
                                "LOCAL",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = RealmsSpacing.xs, vertical = RealmsSpacing.xxs)
                            )
                        }
                    }
                }
                Text(currency, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusTag("REP ${formatSigned(rep)}", repColor)
        }
        Spacer(Modifier.height(RealmsSpacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Balance: $balance $currency",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Rate: 1 gold = ${"%.2f".format(rate)} $currency",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            WealthBars(wealth = wealth)
        }
        Spacer(Modifier.height(RealmsSpacing.s))
        OutlinedButton(
            onClick = onExchange,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small
        ) { Text("Exchange", style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
private fun ExchangeCard(
    faction: com.realmsoffate.game.data.Faction,
    character: com.realmsoffate.game.data.Character,
    onCommit: (direction: String, amount: Int) -> Unit,
    onCancel: () -> Unit
) {
    val wealth = faction.economy?.wealth ?: 3
    val rate = (0.6 + 0.2 * (wealth - 3)).coerceIn(0.3, 1.6)
    var direction by remember { mutableStateOf("to") } // to = gold → local, from = local → gold
    var amountText by remember { mutableStateOf("10") }
    val amount = amountText.toIntOrNull() ?: 0
    val preview = if (direction == "to") (amount * rate).toInt() else (amount / rate).toInt()
    val currentLocal = character.currencyBalances[faction.currency] ?: 0
    val canAfford = if (direction == "to") character.gold >= amount else currentLocal >= amount

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().border(
            1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium
        )
    ) {
        Column(Modifier.padding(RealmsSpacing.m)) {
            Text("EXCHANGE WITH ${faction.name.uppercase()}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(RealmsSpacing.s))
            // Direction toggle
            PanelTabRow(
                tabs = listOf(
                    PanelTab("Gold → ${faction.currency}"),
                    PanelTab("${faction.currency} → Gold")
                ),
                selectedIndex = if (direction == "to") 0 else 1,
                onSelect = { direction = if (it == 0) "to" else "from" },
                horizontalPadding = 0.dp
            )
            Spacer(Modifier.height(RealmsSpacing.s))
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() }.take(6) },
                label = { Text(if (direction == "to") "Gold to spend" else "${faction.currency} to spend") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(RealmsSpacing.s))
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(RealmsSpacing.s)) {
                    Text(
                        "You get: $preview ${if (direction == "to") faction.currency else "gold"}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Rate: 1 gold = ${"%.2f".format(rate)} ${faction.currency}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(RealmsSpacing.s))
            Row(horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.s)) {
                Button(
                    onClick = { onCommit(direction, amount) },
                    enabled = amount > 0 && canAfford,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small
                ) { Text("Exchange") }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small
                ) { Text("Cancel") }
            }
            if (!canAfford && amount > 0) {
                Text(
                    "Insufficient funds.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = RealmsSpacing.xs)
                )
            }
        }
    }
}

@Composable
private fun RatesTable(factions: List<com.realmsoffate.game.data.Faction>) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(RealmsSpacing.s)) {
            Row {
                Text("Currency", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Text("Econ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(70.dp))
                Text("Rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(70.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            factions.forEach { f ->
                val wealth = f.economy?.wealth ?: 3
                val rate = (0.6 + 0.2 * (wealth - 3)).coerceIn(0.3, 1.6)
                Row(Modifier.padding(vertical = RealmsSpacing.xxs)) {
                    Text(
                        f.currency,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Text(
                        f.economy?.level.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(70.dp),
                        maxLines = 1
                    )
                    Text(
                        "${"%.2f".format(rate)}×",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(70.dp)
                    )
                }
            }
        }
    }
}
