package com.realmsoffate.game.ui.panels

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.ui.theme.RealmsTheme

// ----------------- CURRENCY (exchange + rate table) -----------------

@Composable
internal fun CurrencyPanel(
    state: GameUiState,
    onClose: () -> Unit,
    onExchange: (factionName: String, direction: String, amount: Int) -> Unit = { _, _, _ -> }
) {
    val ch = state.character ?: return
    val realms = RealmsTheme.colors
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

    PanelSheet(
        "\uD83D\uDCB0  Coin",
        subtitle = localFactionName?.let { "Local: $it" },
        onClose = onClose
    ) {
        LazyColumn(
            Modifier.padding(horizontal = 14.dp).heightIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Wealth header card.
            item {
                Surface(
                    color = realms.goldAccent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("\uD83D\uDCB0", style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("GOLD ON HAND", style = MaterialTheme.typography.labelMedium, color = realms.goldAccent)
                                Text(
                                    "${ch.gold}",
                                    style = MaterialTheme.typography.displaySmall,
                                    color = realms.goldAccent,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Total wealth (gold-eq): $wealthGold",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Per-faction currency cards with exchange affordance.
            item { SectionCap("LOCAL CURRENCIES") }
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
                        rep >= 50 -> realms.success
                        rep <= -50 -> realms.fumbleRed
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
                SectionCap("EXCHANGE RATES")
                RatesTable(factions)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Rates follow economy strength: richer markets = more favourable swaps. Shops trade in local currency; higher reputation means better prices.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
    val realms = RealmsTheme.colors
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().border(
            1.dp,
            if (isLocal) realms.goldAccent.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant,
            RoundedCornerShape(14.dp)
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (isLocal) {
                            Spacer(Modifier.width(6.dp))
                            Surface(color = realms.goldAccent.copy(alpha = 0.18f), shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    "LOCAL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = realms.goldAccent,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(currency, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusTag("REP ${formatSigned(rep)}", repColor)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Balance: $balance $currency",
                        style = MaterialTheme.typography.labelMedium,
                        color = realms.goldAccent,
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
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onExchange,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Exchange", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun ExchangeCard(
    faction: com.realmsoffate.game.data.Faction,
    character: com.realmsoffate.game.data.Character,
    onCommit: (direction: String, amount: Int) -> Unit,
    onCancel: () -> Unit
) {
    val realms = RealmsTheme.colors
    val wealth = faction.economy?.wealth ?: 3
    val rate = (0.6 + 0.2 * (wealth - 3)).coerceIn(0.3, 1.6)
    var direction by remember { mutableStateOf("to") } // to = gold → local, from = local → gold
    var amountText by remember { mutableStateOf("10") }
    val amount = amountText.toIntOrNull() ?: 0
    val preview = if (direction == "to") (amount * rate).toInt() else (amount / rate).toInt()
    val currentLocal = character.currencyBalances[faction.currency] ?: 0
    val canAfford = if (direction == "to") character.gold >= amount else currentLocal >= amount

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().border(
            1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(14.dp)
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("EXCHANGE WITH ${faction.name.uppercase()}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            // Direction toggle
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = direction == "to",
                    onClick = { direction = "to" },
                    label = { Text("Gold → ${faction.currency}", style = MaterialTheme.typography.labelMedium) }
                )
                FilterChip(
                    selected = direction == "from",
                    onClick = { direction = "from" },
                    label = { Text("${faction.currency} → Gold", style = MaterialTheme.typography.labelMedium) }
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() }.take(6) },
                label = { Text(if (direction == "to") "Gold to spend" else "${faction.currency} to spend") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                color = realms.goldAccent.copy(alpha = 0.14f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text(
                        "You get: $preview ${if (direction == "to") faction.currency else "gold"}",
                        style = MaterialTheme.typography.titleSmall,
                        color = realms.goldAccent,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Rate: 1 gold = ${"%.2f".format(rate)} ${faction.currency}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onCommit(direction, amount) },
                    enabled = amount > 0 && canAfford,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Exchange") }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Cancel") }
            }
            if (!canAfford && amount > 0) {
                Text(
                    "Insufficient funds.",
                    style = MaterialTheme.typography.labelSmall,
                    color = realms.fumbleRed,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun RatesTable(factions: List<com.realmsoffate.game.data.Faction>) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp)) {
            Row {
                Text("Currency", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Text("Econ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(70.dp))
                Text("Rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(70.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            factions.forEach { f ->
                val wealth = f.economy?.wealth ?: 3
                val rate = (0.6 + 0.2 * (wealth - 3)).coerceIn(0.3, 1.6)
                Row(Modifier.padding(vertical = 3.dp)) {
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
                        color = RealmsTheme.colors.goldAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(70.dp)
                    )
                }
            }
        }
    }
}
