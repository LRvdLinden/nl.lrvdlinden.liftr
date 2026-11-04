package nl.lrvdlinden.liftr.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lrvdlinden.liftr.core.LiftrFormat
import nl.lrvdlinden.liftr.core.Muscle

/** Dark scroll page with a big heading, used by every tab (LiftrPage). */
@Composable
fun LiftrPage(
    title: String,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(LiftrColors.background), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 900.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .statusBarsPadding().padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (subtitle != null) {
                        Text(subtitle.uppercase(), color = LiftrColors.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(title.uppercase(), style = LiftrType.display(40.sp))
                }
                trailing()
            }
            content()
        }
    }
}

/** Pushed screen with a back arrow and a title, scrolling content, max width for tablets. */
@Composable
fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    maxWidth: Dp = 760.dp,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(LiftrColors.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = LiftrColors.accent)
            }
            Text(title, Modifier.weight(1f), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            actions()
        }
        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
            val base = Modifier.widthIn(max = maxWidth).fillMaxWidth()
            Column(
                (if (scroll) base.verticalScroll(rememberScrollState()) else base).padding(horizontal = 16.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), style = LiftrType.heading(20.sp), modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, color = LiftrColors.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(6.dp))
        }
    }
}

/** Small stat tile: big number with a label underneath. */
@Composable
fun StatTile(value: String, label: String, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Column(modifier.liftrCard(padding = 14.dp, radius = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (icon != null) Icon(icon, null, tint = LiftrColors.accent, modifier = Modifier.size(20.dp))
        Text(value, style = LiftrType.display(26.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label.uppercase(), color = LiftrColors.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 13.sp)
    }
}

@Composable
fun MuscleIcon(muscle: Muscle, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).background(LiftrColors.accent.copy(alpha = 0.14f), RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(LiftrIcons.muscle(muscle), null, tint = LiftrColors.accent, modifier = Modifier.size(size * 0.5f))
    }
}

/** Big orange call-to-action (LiftrButtonStyle). */
@Composable
fun LiftrButton(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).clip(shape)
            .then(if (filled) Modifier.background(LiftrColors.accentGradient, shape) else Modifier.border(2.dp, LiftrColors.accent, shape))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (filled) Color.White else LiftrColors.accent
        if (icon != null) {
            Icon(icon, null, tint = color.copy(alpha = if (enabled) 1f else 0.5f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
        }
        Text(text.uppercase(), style = LiftrType.heading(18.sp).copy(color = color.copy(alpha = if (enabled) 1f else 0.5f)))
    }
}

/** White capsule button on the orange start card (WhiteButtonStyle). */
@Composable
fun WhiteButton(text: String, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color.White).clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = LiftrColors.accentDeep, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
        }
        Text(text.uppercase(), style = LiftrType.heading(18.sp).copy(color = LiftrColors.accentDeep))
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) LiftrColors.accentGradient else SolidColor(LiftrColors.card))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        color = if (selected) Color.White else LiftrColors.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold,
    )
}

/** Monday-to-Sunday dots for the current week, with weekday and day number. */
@Composable
fun WeekDots(trained: List<Boolean>, labels: List<String>, dayNumbers: List<String>, todayIndex: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until 7) {
            val on = trained.getOrNull(i) == true
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape)
                        .background(if (on) LiftrColors.accentGradient else SolidColor(LiftrColors.cardRaised))
                        .border(2.dp, if (i == todayIndex) LiftrColors.accent else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                val color = if (i == todayIndex) LiftrColors.accent else LiftrColors.muted
                Text(labels.getOrElse(i) { "" }, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(dayNumbers.getOrElse(i) { "" }, color = color, fontSize = 11.sp,
                    fontWeight = if (i == todayIndex) FontWeight.Black else FontWeight.Normal)
            }
        }
    }
}

/**
 * Text field for numbers that accepts both "42,5" and "42.5" and writes through on every keystroke
 * (NumberField). Keeps its own text while typing; follows outside changes.
 */
@Composable
fun NumberField(
    value: Double,
    onValue: (Double) -> Unit,
    decimals: Boolean,
    modifier: Modifier = Modifier,
    done: Boolean = false,
    textStyle: TextStyle = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
) {
    fun format(v: Double): String = if (v == 0.0) "" else if (decimals) LiftrFormat.decimal(v, 2).replace(" ", "") else v.toInt().toString()
    var text by remember { mutableStateOf(format(value)) }
    LaunchedEffect(value) {
        if (LiftrFormat.parse(text) != value && !(text.isEmpty() && value == 0.0)) text = format(value)
    }
    Box(
        modifier.height(44.dp).background(if (done) LiftrColors.success.copy(alpha = 0.18f) else LiftrColors.cardRaised, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { new ->
                val filtered = new.filter { it.isDigit() || (decimals && (it == ',' || it == '.')) }
                text = filtered
                val parsed = LiftrFormat.parse(filtered)
                if (parsed != null) { if (parsed != value) onValue(parsed) } else if (filtered.isEmpty() && value != 0.0) onValue(0.0)
            },
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(LiftrColors.accent),
            keyboardOptions = KeyboardOptions(keyboardType = if (decimals) KeyboardType.Decimal else KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center) {
                    if (text.isEmpty()) Text("0", style = textStyle.copy(color = LiftrColors.muted.copy(alpha = 0.5f)))
                    inner()
                }
            },
        )
    }
}

/** Dark text field in a card. */
@Composable
fun LiftrTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    textStyle: TextStyle = TextStyle(color = Color.White, fontSize = 17.sp),
    monospace: Boolean = false,
) {
    val style = if (monospace) textStyle.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) else textStyle
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        minLines = minLines,
        textStyle = style,
        cursorBrush = SolidColor(LiftrColors.accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().background(LiftrColors.cardRaised, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                if (value.isEmpty()) Text(placeholder, style = style.copy(color = LiftrColors.muted.copy(alpha = 0.7f)))
                inner()
            }
        },
    )
}

/** A settings-like card with rows. */
@Composable
fun CardSection(title: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null) Text(title.uppercase(), color = LiftrColors.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 6.dp))
        Column(Modifier.fillMaxWidth().liftrCard(padding = 0.dp, radius = 16.dp), content = content)
        if (footer != null) Text(footer, color = LiftrColors.muted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(horizontal = 6.dp))
    }
}

@Composable
fun RowItem(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = LiftrColors.accent,
    color: Color = Color.White,
    detail: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(text, color = color, fontSize = 16.sp)
            if (detail != null) Text(detail, color = LiftrColors.muted, fontSize = 12.sp, lineHeight = 15.sp)
        }
        trailing?.invoke()
    }
}

@Composable
fun Divider() {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(LiftrColors.line))
}

/** −/+ stepper row ("Stepper"). */
@Composable
fun StepperRow(label: String, valueText: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    RowItem(label, trailing = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(valueText, color = LiftrColors.accent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            StepButton("−", onMinus)
            StepButton("+", onPlus)
        }
    })
}

@Composable
fun StepButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(LiftrColors.cardRaised).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
}

fun gradientOrSolid(on: Boolean, off: Color): Brush = if (on) LiftrColors.accentGradient else SolidColor(off)
