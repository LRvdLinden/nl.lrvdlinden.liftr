package nl.lrvdlinden.liftr.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lrvdlinden.liftr.core.LiftrData
import nl.lrvdlinden.liftr.core.LiftrFormat
import nl.lrvdlinden.liftr.core.WorkoutStats
import nl.lrvdlinden.liftr.core.tr
import nl.lrvdlinden.liftr.core.trf
import nl.lrvdlinden.liftr.data.Settings

/** A badge you can flip: the medal on the front; on the back when you earned it and your name, or how far along you are. */
@Composable
fun BadgeView(badge: WorkoutStats.Badge, large: Boolean = false, modifier: Modifier = Modifier) {
    var target by rememberSaveable(badge.id) { mutableFloatStateOf(0f) }
    val angle by animateFloatAsState(target, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow), label = "flip")
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current.density
    val width = if (large) 150.dp else 104.dp
    val height = if (large) 190.dp else 150.dp
    val showingBack = (angle % 360f).let { it >= 90f && it < 270f }
    Box(
        (if (large) modifier.fillMaxWidth() else modifier.size(width, height)).height(height)
            .graphicsLayer { rotationY = angle; cameraDistance = 12f * density }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                target += 180f
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
    ) {
        if (!showingBack) BadgeFront(badge, large)
        else Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) { BadgeBack(badge, large) }
    }
}

@Composable
private fun BadgeFront(badge: WorkoutStats.Badge, large: Boolean) {
    val shape = RoundedCornerShape(18.dp)
    val circle = if (large) 84.dp else 64.dp
    Column(
        Modifier.fillMaxSize().background(LiftrColors.card, shape)
            .border(1.dp, if (badge.achieved) LiftrColors.accent.copy(alpha = 0.35f) else LiftrColors.line, shape)
            .graphicsLayer { alpha = if (badge.achieved) 1f else 0.75f }.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (large) 12.dp else 8.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.size(circle)
                .then(if (badge.achieved) Modifier.shadow(10.dp, CircleShape, ambientColor = LiftrColors.accent, spotColor = LiftrColors.accent) else Modifier)
                .clip(CircleShape).background(if (badge.achieved) LiftrColors.accentGradient else SolidColor(LiftrColors.cardRaised)),
            contentAlignment = Alignment.Center,
        ) {
            if (badge.achieved) {
                Box(Modifier.fillMaxSize().padding(4.dp).border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape))
            } else if (badge.progress > 0) {
                Canvas(Modifier.fillMaxSize().padding(2.dp)) {
                    drawArc(LiftrColors.accent, -90f, (360 * badge.progress).toFloat(), false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            Icon(LiftrIcons.symbol(badge.symbol), null, tint = if (badge.achieved) Color.White else LiftrColors.muted.copy(alpha = 0.6f),
                modifier = Modifier.size(if (large) 36.dp else 28.dp))
        }
        Text(badge.title, color = if (badge.achieved) Color.White else LiftrColors.muted, fontSize = if (large) 15.sp else 12.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis,
            lineHeight = if (large) 18.sp else 14.sp)
    }
}

@Composable
private fun BadgeBack(badge: WorkoutStats.Badge, large: Boolean) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxSize().background(if (badge.achieved) LiftrColors.accentGradient else SolidColor(LiftrColors.cardRaised), shape).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (large) 8.dp else 5.dp, Alignment.CenterVertically),
    ) {
        val date = badge.date
        if (date != null) {
            Icon(Icons.Filled.Verified, null, tint = Color.White, modifier = Modifier.size(if (large) 28.dp else 22.dp))
            Text(tr("EARNED", "BEHAALD"), color = Color.White.copy(alpha = 0.85f), fontSize = if (large) 11.sp else 9.sp,
                fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(LiftrFormat.mediumDate(date), color = Color.White, fontSize = if (large) 17.sp else 13.sp, fontWeight = FontWeight.Black,
                maxLines = 1, textAlign = TextAlign.Center)
            val name = Settings.userName.trim()
            if (name.isNotEmpty()) {
                Text(trf("by {1}", "door {1}", name), color = Color.White.copy(alpha = 0.9f), fontSize = if (large) 13.sp else 11.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, textAlign = TextAlign.Center)
            }
        } else {
            Text(badge.detail, color = Color.White, fontSize = if (large) 13.sp else 11.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, maxLines = 4, lineHeight = if (large) 16.sp else 13.sp)
            if (badge.progressText.isNotEmpty()) {
                LinearProgressIndicator(progress = { badge.progress.toFloat() }, color = LiftrColors.accent,
                    trackColor = LiftrColors.card, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp))
                Text(badge.progressText, color = LiftrColors.muted, fontSize = if (large) 12.sp else 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            } else {
                Text(tr("Not earned yet", "Nog niet behaald"), color = LiftrColors.muted, fontSize = if (large) 12.sp else 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Every badge, grouped, with how many you have. */
@Composable
fun BadgesScreen(data: LiftrData) {
    val badges = remember(data) { WorkoutStats.badges(data) }
    val earned = badges.count { it.achieved }
    DetailScaffold(title = tr("Badges", "Badges"), onBack = { Nav.pop() }, maxWidth = 900.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(trf("{1} of {2} earned", "{1} van {2} behaald", earned, badges.size).uppercase(), style = LiftrType.heading(22.sp))
            LinearProgressIndicator(progress = { earned.toFloat() / maxOf(1, badges.size) }, color = LiftrColors.accent,
                trackColor = LiftrColors.cardRaised, modifier = Modifier.fillMaxWidth())
            Text(tr("Tap a badge to flip it.", "Tik op een badge om hem om te draaien."), color = LiftrColors.muted, fontSize = 13.sp)
        }
        for (category in WorkoutStats.BadgeCategory.entries) {
            val items = badges.filter { it.category == category }
            if (items.isEmpty()) continue
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(category.title.uppercase(), style = LiftrType.heading(18.sp), modifier = Modifier.weight(1f))
                    Text("${items.count { it.achieved }}/${items.size}", color = LiftrColors.muted, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val columns = maxOf(2, (maxWidth.value / 162).toInt())
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (row in items.chunked(columns)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                for (badge in row) BadgeView(badge, large = true, modifier = Modifier.weight(1f))
                                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
