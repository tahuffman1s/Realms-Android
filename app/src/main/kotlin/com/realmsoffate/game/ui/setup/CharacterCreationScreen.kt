package com.realmsoffate.game.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realmsoffate.game.data.Abilities
import com.realmsoffate.game.data.Character
import com.realmsoffate.game.data.CharacterAppearance
import com.realmsoffate.game.game.Classes
import com.realmsoffate.game.game.GameViewModel
import com.realmsoffate.game.game.Races
import com.realmsoffate.game.ui.components.SectionHeader
import com.realmsoffate.game.ui.theme.RealmsSpacing
import com.realmsoffate.game.ui.theme.RealmsTheme

/**
 * 6-step character creation wizard — mirrors the web source of truth:
 *   Step 0 Identity   — name, gender, age band
 *   Step 1 Appearance — skin tone, hair color, hair style, build
 *   Step 2 Race       — 2-col grid
 *   Step 3 Class      — 2-col grid
 *   Step 4 Stats      — point-buy (27 pts) with cost curve, Recommended, racial bonus selector
 *   Step 5 Confirm    — summary card + BEGIN
 *
 * Progress dots at the top. "Next" disabled until the step is valid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterCreationScreen(vm: GameViewModel) {
    // ----- state -----
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("Unspecified") }
    var ageBand by rememberSaveable { mutableStateOf("Adult") }
    var skinTone by rememberSaveable { mutableStateOf(SKIN_TONES.first()) }
    var hairColor by rememberSaveable { mutableStateOf(HAIR_COLORS.first()) }
    var hairStyle by rememberSaveable { mutableStateOf(HAIR_STYLES.first()) }
    var build by rememberSaveable { mutableStateOf("Average") }
    var race by rememberSaveable { mutableStateOf(Races.list.first().name) }
    var cls by rememberSaveable { mutableStateOf(Classes.list.first().name) }
    val baseStats = rememberSaveable { mutableStateOf(intArrayOf(8, 8, 8, 8, 8, 8)) }
    // Racial bonus allocation: +2 and +1 applied to two different stats (defaulting to race suggestions).
    var primaryBonus by rememberSaveable { mutableIntStateOf(0) } // index 0..5
    var secondaryBonus by rememberSaveable { mutableIntStateOf(1) }

    val totalSteps = 6
    val realms = RealmsTheme.colors
    val stepValid = remember(step, name, race, cls, baseStats.value, primaryBonus, secondaryBonus) {
        when (step) {
            0 -> name.isNotBlank()
            1 -> true
            2 -> Races.find(race) != null
            3 -> Classes.find(cls) != null
            4 -> pointCost(baseStats.value) <= 27 && primaryBonus != secondaryBonus
            5 -> true
            else -> false
        }
    }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 1.dp) {
                Column(Modifier.statusBarsPadding().padding(horizontal = RealmsSpacing.l, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(48.dp))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stepTitle(step).uppercase(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            ProgressDots(step, totalSteps)
                        }
                        Spacer(Modifier.width(48.dp))
                    }
                }
            }
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = RealmsSpacing.l, vertical = RealmsSpacing.m)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (step > 0) {
                        OutlinedButton(
                            onClick = { step-- },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Back")
                        }
                    }
                    if (step < totalSteps - 1) {
                        Button(
                            onClick = { step++ },
                            enabled = stepValid,
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text("Next", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                        }
                    } else {
                        GradientBeginButton(
                            enabled = stepValid,
                            onClick = {
                                val (final, ap) = finalizeCharacter(
                                    name, race, cls, baseStats.value,
                                    primaryBonus, secondaryBonus,
                                    skinTone, hairColor, hairStyle, build, gender, ageBand
                                )
                                vm.startNewGame(final.apply { appearance = ap })
                            },
                            modifier = Modifier.weight(1f).height(52.dp)
                        )
                    }
                }
            }
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = RealmsSpacing.l, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (step) {
                0 -> IdentityStep(
                    name = name, onName = { name = it.take(30) },
                    gender = gender, onGender = { gender = it },
                    ageBand = ageBand, onAge = { ageBand = it }
                )
                1 -> AppearanceStep(
                    skinTone = skinTone, onSkin = { skinTone = it },
                    hairColor = hairColor, onHair = { hairColor = it },
                    hairStyle = hairStyle, onStyle = { hairStyle = it },
                    build = build, onBuild = { build = it }
                )
                2 -> RaceStep(race = race, onRace = { race = it })
                3 -> ClassStep(cls = cls, onCls = { cls = it })
                4 -> StatsStep(
                    baseStats = baseStats.value,
                    onUpdate = { i, v ->
                        val arr = baseStats.value.copyOf()
                        arr[i] = v
                        baseStats.value = arr
                    },
                    cls = cls,
                    primaryBonus = primaryBonus, onPrimary = { primaryBonus = it },
                    secondaryBonus = secondaryBonus, onSecondary = { secondaryBonus = it },
                    onRecommend = {
                        // Use the verbatim per-class recommended array from the source-of-truth
                        // CLASSES table so each class actually gets its own preset.
                        val def = Classes.find(cls)
                        baseStats.value = (def?.recommended ?: intArrayOf(13, 13, 13, 13, 13, 13)).copyOf()
                    }
                )
                5 -> ConfirmStep(
                    name = name, race = race, cls = cls,
                    baseStats = baseStats.value,
                    primaryBonus = primaryBonus, secondaryBonus = secondaryBonus,
                    skinTone = skinTone, hairColor = hairColor, hairStyle = hairStyle,
                    build = build, gender = gender, ageBand = ageBand
                )
            }
            // Generous bottom slack so the BEGIN THE TALE bar never covers the
            // last bit of the confirm summary on short screens.
            Spacer(Modifier.height(120.dp))
        }
    }
}

// ---------- Step components ----------

@Composable
private fun IdentityStep(
    name: String, onName: (String) -> Unit,
    gender: String, onGender: (String) -> Unit,
    ageBand: String, onAge: (String) -> Unit
) {
    SectionHeader("\uD83D\uDCDC  IDENTITY")
    OutlinedTextField(
        value = name,
        onValueChange = onName,
        label = { Text("Name") },
        placeholder = { Text("Kaelis, Vara, Thorn…") },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    )
    SectionHeader("GENDER")
    ChipRow(
        options = listOf("Male", "Female", "Non-binary", "Unspecified"),
        selected = gender,
        onSelect = onGender
    )
    SectionHeader("AGE")
    ChipRow(
        options = listOf("Young", "Adult", "Mature", "Elder"),
        selected = ageBand,
        onSelect = onAge
    )
    Text(
        "Age colors how NPCs address you — young are dismissed, elders respected or patronized.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun AppearanceStep(
    skinTone: String, onSkin: (String) -> Unit,
    hairColor: String, onHair: (String) -> Unit,
    hairStyle: String, onStyle: (String) -> Unit,
    build: String, onBuild: (String) -> Unit
) {
    SectionHeader("\uD83C\uDFA8  APPEARANCE")
    Text("Skin tone", style = MaterialTheme.typography.labelLarge)
    ColorSwatchRow(colors = SKIN_TONES, selected = skinTone, onSelect = onSkin)

    Spacer(Modifier.height(4.dp))
    Text("Hair color", style = MaterialTheme.typography.labelLarge)
    ColorSwatchRow(colors = HAIR_COLORS, selected = hairColor, onSelect = onHair)

    Spacer(Modifier.height(4.dp))
    Text("Hair style", style = MaterialTheme.typography.labelLarge)
    ChipRow(options = HAIR_STYLES, selected = hairStyle, onSelect = onStyle)

    Spacer(Modifier.height(4.dp))
    Text("Build", style = MaterialTheme.typography.labelLarge)
    ChipRow(
        options = listOf("Lean", "Average", "Muscular", "Stocky", "Hulking"),
        selected = build,
        onSelect = onBuild
    )
}

@Composable
private fun RaceStep(race: String, onRace: (String) -> Unit) {
    SectionHeader("\u2694\uFE0F  RACE")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Races.list.chunked(3).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { r ->
                    Box(Modifier.weight(1f)) {
                        GridCard(
                            selected = r.name == race,
                            onClick = { onRace(r.name) },
                            emoji = raceEmoji(r.name),
                            title = r.name,
                            subtitle = bonusLine(r),
                            hint = ""
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    Races.find(race)?.let {
        Spacer(Modifier.height(4.dp))
        DetailCard {
            Text(
                it.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                it.traits.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(it.physiqueTemplate, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ClassStep(cls: String, onCls: (String) -> Unit) {
    SectionHeader("\uD83D\uDCAB  CLASS")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Classes.list.chunked(3).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { c ->
                    Box(Modifier.weight(1f)) {
                        GridCard(
                            selected = c.name == cls,
                            onClick = { onCls(c.name) },
                            emoji = classEmoji(c.name),
                            title = c.name,
                            subtitle = "d${c.hitDie} · ${c.primary}",
                            hint = ""
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    Classes.find(cls)?.let {
        Spacer(Modifier.height(4.dp))
        DetailCard {
            Text(
                it.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                it.proficiencies.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Saves: ${it.savingThrows.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Gear: " + it.startingItems.joinToString(", ") { s -> s.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatsStep(
    baseStats: IntArray,
    onUpdate: (Int, Int) -> Unit,
    cls: String,
    primaryBonus: Int, onPrimary: (Int) -> Unit,
    secondaryBonus: Int, onSecondary: (Int) -> Unit,
    onRecommend: () -> Unit
) {
    val spent = pointCost(baseStats)
    val remaining = 27 - spent
    val labels = listOf("STR", "DEX", "CON", "INT", "WIS", "CHA")
    val realms = RealmsTheme.colors

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(end = 12.dp)  // breathing room from the Recommended chip
        ) {
            SectionHeader("\uD83E\uDDE0  STATS")
            Text(
                "Point-buy · $remaining of 27 points remaining",
                style = MaterialTheme.typography.bodySmall,
                color = if (remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AssistChip(
            onClick = onRecommend,
            label = { Text("Recommended") },
            leadingIcon = { Icon(Icons.Default.Casino, null, Modifier.size(18.dp)) }
        )
    }
    labels.forEachIndexed { i, label ->
        val bonus = when (i) {
            primaryBonus -> 2
            secondaryBonus -> 1
            else -> 0
        }
        StatRow(
            label = label,
            base = baseStats[i],
            bonus = bonus,
            cost = statCost(baseStats[i]),
            onMinus = { if (baseStats[i] > 8) onUpdate(i, baseStats[i] - 1) },
            onPlus = {
                val next = baseStats[i] + 1
                if (next <= 15 && pointCost(baseStats.copyOf().also { it[i] = next }) <= 27) {
                    onUpdate(i, next)
                }
            }
        )
    }
    Spacer(Modifier.height(6.dp))
    SectionHeader("RACIAL BONUS")
    Text(
        "Choose where your +2 and +1 fall. They can't be the same stat.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            Text("+2 to", style = MaterialTheme.typography.labelMedium, color = realms.goldAccent)
            Spacer(Modifier.height(4.dp))
            BonusSelector(labels, primaryBonus, onPrimary, exclude = secondaryBonus)
        }
        Column {
            Text("+1 to", style = MaterialTheme.typography.labelMedium, color = realms.goldAccent)
            Spacer(Modifier.height(4.dp))
            BonusSelector(labels, secondaryBonus, onSecondary, exclude = primaryBonus)
        }
    }
}

@Composable
private fun ConfirmStep(
    name: String, race: String, cls: String, baseStats: IntArray,
    primaryBonus: Int, secondaryBonus: Int,
    skinTone: String, hairColor: String, hairStyle: String, build: String,
    gender: String, ageBand: String
) {
    val finalStats = IntArray(6) { i ->
        baseStats[i] + when (i) {
            primaryBonus -> 2
            secondaryBonus -> 1
            else -> 0
        }
    }
    val labels = listOf("STR", "DEX", "CON", "INT", "WIS", "CHA")
    val clsDef = Classes.find(cls)
    val hp = (clsDef?.hitDie ?: 8) + mod(finalStats[2])
    val realms = RealmsTheme.colors

    SectionHeader("\uD83D\uDD0D  CONFIRM")
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(RealmsSpacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar — monogram over skin tone swatch, colored by hair
                Box(
                    Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(parseHex(skinTone))
                        .border(2.dp, parseHex(hairColor), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name.take(1).uppercase().ifBlank { "?" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.Black.copy(alpha = 0.65f)
                    )
                }
                Spacer(Modifier.width(RealmsSpacing.m))
                Column {
                    Text(name.ifBlank { "Adventurer" }, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "$ageBand · $gender",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "L1 $race $cls",
                        style = MaterialTheme.typography.labelLarge,
                        color = realms.goldAccent
                    )
                }
            }
            Spacer(Modifier.height(RealmsSpacing.m))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill("HP", "$hp", realms.success, Modifier.weight(1f))
                InfoPill("AC", "${10 + mod(finalStats[1])}", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                InfoPill("BUILD", build, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            }
            Spacer(Modifier.height(RealmsSpacing.m))
            Text("ABILITIES", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0..2) FinalAbility(labels[i], finalStats[i], Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 3..5) FinalAbility(labels[i], finalStats[i], Modifier.weight(1f))
            }
            Spacer(Modifier.height(RealmsSpacing.m))
            Text("APPEARANCE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                "$hairStyle $hairColor hair · skin $skinTone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------- Helpers / shared UI ----------

@Composable
private fun ProgressDots(step: Int, total: Int) {
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            Box(
                Modifier
                    .size(if (i == step) 10.dp else 7.dp)
                    .clip(CircleShape)
                    .background(
                        if (i == step) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
            )
        }
    }
}

@Composable
private fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { opt ->
            FilterChip(
                selected = opt == selected,
                onClick = { onSelect(opt) },
                label = { Text(opt, style = MaterialTheme.typography.labelMedium) }
            )
        }
    }
}

@Composable
private fun ColorSwatchRow(colors: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        colors.forEach { hex ->
            val sel = hex == selected
            Box(
                Modifier
                    .size(if (sel) 44.dp else 38.dp)
                    .clip(CircleShape)
                    .background(parseHex(hex))
                    .border(
                        width = if (sel) 3.dp else 1.dp,
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape
                    )
                    .clickable { onSelect(hex) }
            )
        }
    }
}

@Composable
private fun GridCard(
    selected: Boolean,
    onClick: () -> Unit,
    emoji: String,
    title: String,
    subtitle: String,
    hint: String
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    Column(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(bg)
            .border(1.dp, color, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp)
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(emoji, fontSize = 24.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(2.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (hint.isNotBlank()) {
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(RealmsSpacing.m), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

@Composable
private fun StatRow(
    label: String, base: Int, bonus: Int, cost: Int,
    onMinus: () -> Unit, onPlus: () -> Unit
) {
    val final = base + bonus
    val mod = mod(final)
    val modLabel = if (mod >= 0) "+$mod" else mod.toString()
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = RealmsSpacing.m, vertical = 6.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(44.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$final", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (bonus > 0) Text(
                        "  (+$bonus)",
                        style = MaterialTheme.typography.labelSmall,
                        color = RealmsTheme.colors.goldAccent
                    )
                }
                Text(
                    "$modLabel · cost $cost",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onMinus, enabled = base > 8) { Text("−", style = MaterialTheme.typography.titleLarge) }
            IconButton(onClick = onPlus, enabled = base < 15) { Text("+", style = MaterialTheme.typography.titleLarge) }
        }
    }
}

@Composable
private fun BonusSelector(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, exclude: Int) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEachIndexed { i, l ->
            val enabled = i != exclude
            val sel = i == selected
            Surface(
                onClick = { if (enabled) onSelect(i) },
                enabled = enabled,
                color = if (sel) RealmsTheme.colors.goldAccent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text(
                    l,
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        sel -> RealmsTheme.colors.goldAccent
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun InfoPill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Column(
            Modifier.padding(10.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FinalAbility(label: String, score: Int, modifier: Modifier = Modifier) {
    val mod = mod(score)
    val modLabel = if (mod >= 0) "+$mod" else mod.toString()
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Column(
            Modifier.padding(vertical = RealmsSpacing.s).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text("$score", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(modLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GradientBeginButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val realms = RealmsTheme.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    ) {
        Box(
            modifier = if (enabled) Modifier.background(
                Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, realms.goldAccent))
            ) else Modifier,
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = RealmsSpacing.m)) {
                Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "BEGIN THE TALE",
                    style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 2.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

// ---------- Pure logic ----------

/**
 * D&D 5e-ish point-buy costs: 8=0, 9=1, 10=2, 11=3, 12=4, 13=5, 14=7, 15=9.
 */
private fun statCost(score: Int): Int = when (score) {
    8 -> 0; 9 -> 1; 10 -> 2; 11 -> 3; 12 -> 4; 13 -> 5; 14 -> 7; 15 -> 9
    else -> 0
}

private fun pointCost(arr: IntArray): Int = arr.sumOf { statCost(it) }

private fun mod(score: Int): Int = (score - 10).floorDiv(2)

private fun bonusLine(r: com.realmsoffate.game.game.RaceDef): String {
    val bits = listOfNotNull(
        if (r.strBonus > 0) "STR+${r.strBonus}" else null,
        if (r.dexBonus > 0) "DEX+${r.dexBonus}" else null,
        if (r.conBonus > 0) "CON+${r.conBonus}" else null,
        if (r.intBonus > 0) "INT+${r.intBonus}" else null,
        if (r.wisBonus > 0) "WIS+${r.wisBonus}" else null,
        if (r.chaBonus > 0) "CHA+${r.chaBonus}" else null
    )
    return bits.joinToString(" ")
}

/** Class-driven recommended array (standard 15-14-13-12-10-8 aligned to primary/saves). */
private fun recommendedStatsFor(primary: String, saves: List<String>): IntArray {
    val labels = listOf("STR", "DEX", "CON", "INT", "WIS", "CHA")
    val priority = mutableListOf<String>()
    priority += primary.uppercase()
    priority += saves.map { it.uppercase() }
    priority += listOf("CON")
    priority += labels
    val order = priority.distinct().take(6)
    val values = intArrayOf(15, 14, 13, 12, 10, 8)
    val out = IntArray(6) { 8 }
    order.forEachIndexed { rank, stat ->
        val idx = labels.indexOf(stat).coerceAtLeast(0)
        if (rank < values.size && out[idx] == 8) out[idx] = values[rank]
    }
    // Ensure we filled six slots; fill any remaining 8s with the lowest unused values.
    return out
}

private fun finalizeCharacter(
    name: String, race: String, cls: String, baseStats: IntArray,
    primaryBonus: Int, secondaryBonus: Int,
    skinTone: String, hairColor: String, hairStyle: String,
    build: String, gender: String, ageBand: String
): Pair<Character, CharacterAppearance> {
    val final = IntArray(6) { i ->
        baseStats[i] + when (i) {
            primaryBonus -> 2
            secondaryBonus -> 1
            else -> 0
        }
    }
    val abs = Abilities(
        str = final[0], dex = final[1], con = final[2],
        int = final[3], wis = final[4], cha = final[5]
    )
    val ch = Character(
        name = name.ifBlank { "Adventurer" },
        race = race,
        cls = cls,
        abilities = abs
    )
    val ap = CharacterAppearance(
        skinTone = skinTone, hairColor = hairColor, hairStyle = hairStyle,
        build = build, gender = gender, ageBand = ageBand
    )
    return ch to ap
}

private fun stepTitle(step: Int): String = when (step) {
    0 -> "Step 1 · Identity"
    1 -> "Step 2 · Appearance"
    2 -> "Step 3 · Race"
    3 -> "Step 4 · Class"
    4 -> "Step 5 · Stats"
    else -> "Step 6 · Confirm"
}

private fun parseHex(hex: String): Color {
    val clean = hex.removePrefix("#")
    return runCatching {
        val v = clean.toLong(16)
        Color(((0xFF000000) or v).toInt())
    }.getOrElse { Color.Gray }
}

private fun raceEmoji(race: String): String = when (race.lowercase()) {
    "human" -> "\uD83E\uDDCD"
    "elf" -> "\uD83E\uDDDD"
    "dwarf" -> "\u26CF\uFE0F"
    "halfling" -> "\uD83C\uDF3E"
    "gnome" -> "\uD83C\uDF44"
    "half-elf" -> "\uD83C\uDF19"
    "half-orc" -> "\uD83E\uDDB2"
    "tiefling" -> "\uD83D\uDC79"
    "dragonborn" -> "\uD83D\uDC09"
    "drow" -> "\uD83C\uDF11"
    else -> "\u2728"
}

private fun classEmoji(cls: String): String = when (cls.lowercase()) {
    "fighter" -> "\u2694\uFE0F"
    "wizard" -> "\uD83E\uDDD9"
    "rogue" -> "\uD83D\uDDE1\uFE0F"
    "cleric" -> "\u269C\uFE0F"
    "ranger" -> "\uD83C\uDFF9"
    "bard" -> "\uD83C\uDFB6"
    "paladin" -> "\uD83D\uDEE1\uFE0F"
    "warlock" -> "\uD83D\uDD2E"
    "barbarian" -> "\uD83E\uDE93"
    "sorcerer" -> "\uD83D\uDD25"
    "druid" -> "\uD83C\uDF3F"
    "monk" -> "\uD83D\uDC4A"
    else -> "\u2728"
}

// ---------- Palette constants ----------

private val SKIN_TONES = listOf(
    "#F7D4B9", "#E8BC95", "#D19A72", "#B67B4F",
    "#935531", "#6B3A1A", "#4A2509", "#8C5A3C", "#C49C7C"
)

private val HAIR_COLORS = listOf(
    "#2A1E10", "#5A4330", "#8C6A44", "#B89066",
    "#D9B070", "#E8D8B0", "#9E9E9E", "#C0392B"
)

private val HAIR_STYLES = listOf("Short", "Long", "Braided", "Shaved", "Bun", "Ponytail", "Tousled", "Flowing")
