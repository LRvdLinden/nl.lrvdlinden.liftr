package nl.lrvdlinden.liftr.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import nl.lrvdlinden.liftr.core.Muscle

/** Material equivalents of the SF Symbols the iOS app uses. */
object LiftrIcons {
    fun muscle(m: Muscle): ImageVector = when (m) {
        Muscle.Chest -> Icons.Filled.FitnessCenter
        Muscle.Back -> Icons.Filled.Rowing
        Muscle.Shoulders -> Icons.Filled.AccessibilityNew
        Muscle.Arms -> Icons.Filled.SportsMma
        Muscle.Legs -> Icons.Filled.DirectionsWalk
        Muscle.Glutes -> Icons.Filled.SportsMartialArts
        Muscle.Core -> Icons.Filled.SelfImprovement
        Muscle.Cardio -> Icons.Filled.DirectionsRun
        Muscle.FullBody -> Icons.Filled.SportsGymnastics
    }

    fun symbol(name: String): ImageVector = when (name) {
        "trophy.fill" -> Icons.Filled.EmojiEvents
        "medal.fill" -> Icons.Filled.MilitaryTech
        "flame.fill" -> Icons.Filled.LocalFireDepartment
        "scalemass.fill", "scalemass" -> Icons.Filled.MonitorWeight
        "square.stack.3d.up.fill", "square.stack.3d.up" -> Icons.Filled.Layers
        "repeat" -> Icons.Filled.Repeat
        "star.fill" -> Icons.Filled.Star
        "dumbbell.fill" -> Icons.Filled.FitnessCenter
        "bolt.fill" -> Icons.Filled.Bolt
        "square.grid.3x3.fill" -> Icons.Filled.Apps
        "sunrise.fill" -> Icons.Filled.WbTwilight
        "moon.stars.fill" -> Icons.Filled.NightsStay
        "calendar.badge.checkmark" -> Icons.Filled.EventAvailable
        "5.circle.fill" -> Icons.Filled.Looks5
        "timer" -> Icons.Filled.Timer
        "sparkles" -> Icons.Filled.AutoAwesome
        "figure.run" -> Icons.Filled.DirectionsRun
        "calendar" -> Icons.Filled.CalendarMonth
        "clock", "clock.fill" -> Icons.Filled.Schedule
        "heart.fill" -> Icons.Filled.Favorite
        "percent" -> Icons.Filled.Percent
        else -> Icons.Filled.EmojiEvents
    }
}
