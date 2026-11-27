package ar.matiasblaiotta.cuenotebooks.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Bg = Color(0xFF0B0D10)
val Panel = Color(0xFF12151A)
val Line = Color(0xFF3A414C)
val LineStrong = Color(0xFF8A94A3)
val TextHi = Color(0xFFF5F7FA)
val TextMid = Color(0xFFD0D5DD)
val Accent = Color(0xFFFFB000)

private val scheme = darkColorScheme(
    primary = Accent, onPrimary = Color(0xFF0B0D10),
    background = Bg, onBackground = TextHi,
    surface = Bg, onSurface = TextHi,
    surfaceVariant = Panel, onSurfaceVariant = TextMid,
    surfaceContainer = Panel, surfaceContainerHigh = Panel, surfaceContainerHighest = Panel,
    outline = LineStrong, outlineVariant = Line,
    error = Color(0xFFFF8A80),
)

/** Números tabulares en todo el texto numérico. */
const val TNUM = "tnum"

@Composable
fun QTheme(content: @Composable () -> Unit) {
    val base = Typography()
    fun TextStyle.q(color: Color = TextHi) = copy(color = color, fontFeatureSettings = TNUM)
    val typo = base.copy(
        titleLarge = base.titleLarge.q().copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.q().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.q(),
        bodyLarge = base.bodyLarge.q(), bodyMedium = base.bodyMedium.q(TextMid),
        bodySmall = base.bodySmall.q(TextMid), labelLarge = base.labelLarge.q(),
        labelMedium = base.labelMedium.q(TextMid).copy(fontSize = 12.sp),
        labelSmall = base.labelSmall.q(TextMid),
    )
    MaterialTheme(colorScheme = scheme, typography = typo, content = content)
}

/** Tarjeta plana: borde de 1dp, sin sombra ni elevación tonal. */
@Composable
fun FlatCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Panel,
        border = BorderStroke(1.dp, Line),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            (if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(14.dp),
            content = content,
        )
    }
}

@Composable
fun QChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        modifier = Modifier.height(36.dp),
        selected = selected, onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        shape = RoundedCornerShape(8.dp),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected,
            borderColor = Line, selectedBorderColor = Accent, borderWidth = 1.dp, selectedBorderWidth = 1.dp,
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Bg, labelColor = TextHi,
            selectedContainerColor = Panel, selectedLabelColor = Accent,
        ),
        elevation = null,
    )
}

@Composable
fun QField(
    value: String, onChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, minLines: Int = 1, singleLine: Boolean = false,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier,
        label = { Text(label) }, minLines = minLines, singleLine = singleLine,
        shape = RoundedCornerShape(8.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent, unfocusedBorderColor = LineStrong,
            focusedLabelColor = Accent, unfocusedLabelColor = TextMid,
            cursorColor = Accent, focusedTextColor = TextHi, unfocusedTextColor = TextHi,
        ),
    )
}

@Composable
fun QButton(label: String, onClick: () -> Unit, primary: Boolean = true, enabled: Boolean = true) {
    if (primary) Button(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(8.dp),
        elevation = null,
        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF0B0D10)),
    ) { Text(label, style = MaterialTheme.typography.labelLarge.copy(color = Color(0xFF0B0D10))) }
    else OutlinedButton(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, LineStrong),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun Header(text: String) =
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium.copy(color = TextHi, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))

@Composable
fun Tag(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp), color = Bg, border = BorderStroke(1.dp, LineStrong),
        shadowElevation = 0.dp, tonalElevation = 0.dp,
    ) { Text(text, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium.copy(color = TextHi)) }
}
