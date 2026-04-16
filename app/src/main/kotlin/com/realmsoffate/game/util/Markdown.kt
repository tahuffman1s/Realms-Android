package com.realmsoffate.game.util

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realmsoffate.game.ui.theme.RealmsTheme

/**
 * Narration markdown renderer — matches the web source's vocabulary:
 *   **bold**, *italic*, `code`, ~~strikethrough~~
 *   ### H3 header (Cinzel gold)
 *   - bullet list (gold diamond ❖ prefix)
 *   > blockquote (gold left border, italic)
 *   --- horizontal rule (gold tinted)
 */
@Composable
fun NarrationMarkdown(
    text: String,
    modifier: Modifier = Modifier,
    baseStyle: TextStyle? = null
) {
    val style = baseStyle ?: MaterialTheme.typography.bodyLarge
    val realms = RealmsTheme.colors
    val boldColor = realms.goldAccent
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val codeText = MaterialTheme.colorScheme.onSurfaceVariant
    val lines = text.trim().lines()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.height(4.dp))
                line.startsWith("### ") -> {
                    Text(
                        parseInline(line.removePrefix("### "), boldColor = boldColor, codeBackground = codeBackground, codeText = codeText),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = realms.goldAccent,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                line.startsWith("## ") -> {
                    Text(
                        parseInline(line.removePrefix("## "), boldColor = boldColor, codeBackground = codeBackground, codeText = codeText),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = realms.goldAccent,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                line.startsWith("# ") -> {
                    Text(
                        parseInline(line.removePrefix("# "), boldColor = boldColor, codeBackground = codeBackground, codeText = codeText),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        ),
                        color = realms.goldAccent,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                line == "---" || line == "***" -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .padding(vertical = 0.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        realms.goldAccent.copy(alpha = 0.6f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    Row(Modifier.padding(start = 4.dp)) {
                        Text(
                            "❖",
                            style = style.copy(color = realms.goldAccent, fontSize = 12.sp),
                            color = realms.goldAccent,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            parseInline(line.removePrefix("- ").removePrefix("* "), boldColor = boldColor, codeBackground = codeBackground, codeText = codeText),
                            style = style,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                line.startsWith("> ") -> {
                    androidx.compose.material3.Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp, bottom = 2.dp)
                    ) {
                        Text(
                            text = parseInline(line.removePrefix("> "), boldColor = boldColor, codeBackground = codeBackground, codeText = codeText),
                            style = style.copy(fontStyle = FontStyle.Italic),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                else -> {
                    Text(
                        text = parseInline(line, boldColor = boldColor, codeBackground = codeBackground, codeText = codeText),
                        style = style,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

fun parseInline(
    s: String,
    boldColor: Color = Color.Unspecified,
    italicColor: Color = Color.Unspecified,
    codeBackground: Color = Color.Unspecified,
    codeText: Color = Color.Unspecified
): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        // ***bold italic***
        if (i + 2 < s.length && s[i] == '*' && s[i + 1] == '*' && s[i + 2] == '*') {
            val end = s.indexOf("***", i + 3)
            if (end > 0) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, color = boldColor)) {
                    append(s.substring(i + 3, end))
                }
                i = end + 3; continue
            }
        }
        // **bold**
        if (i + 1 < s.length && s[i] == '*' && s[i + 1] == '*') {
            val end = s.indexOf("**", i + 2)
            if (end > 0) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = boldColor)) {
                    append(s.substring(i + 2, end))
                }
                i = end + 2; continue
            }
        }
        // *italic* — guard against unclosed ** falling through
        if (s[i] == '*' && !(i + 1 < s.length && s[i + 1] == '*')) {
            val end = s.indexOf('*', i + 1)
            if (end > 0) {
                withStyle(SpanStyle(
                    fontStyle = FontStyle.Italic,
                    color = italicColor
                )) {
                    append(s.substring(i + 1, end))
                }
                i = end + 1; continue
            }
        }
        // ~~strikethrough~~
        if (i + 1 < s.length && s[i] == '~' && s[i + 1] == '~') {
            val end = s.indexOf("~~", i + 2)
            if (end > 0) {
                withStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)) {
                    append(s.substring(i + 2, end))
                }
                i = end + 2; continue
            }
        }
        // `code`
        if (s[i] == '`') {
            val end = s.indexOf('`', i + 1)
            if (end > 0) {
                withStyle(SpanStyle(background = codeBackground, fontFamily = FontFamily.Monospace, color = codeText)) {
                    append(s.substring(i + 1, end))
                }
                i = end + 1; continue
            }
        }
        append(s[i])
        i++
    }
}
