package nl.lrvdlinden.liftr.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import nl.lrvdlinden.liftr.MainActivity
import nl.lrvdlinden.liftr.core.L10n
import nl.lrvdlinden.liftr.core.LiftrRepository
import nl.lrvdlinden.liftr.core.Prefs
import nl.lrvdlinden.liftr.core.WorkoutStats
import nl.lrvdlinden.liftr.core.tr

/** Home-screen widget "This week": the training days, workouts this week and the week streak (LiftrWidgets.swift). */
class WeekWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Prefs.init(context)
        L10n.init(context)
        val repo = LiftrRepository.get(context)
        repo.ensureLoaded()
        val data = repo.current
        val trained = WorkoutStats.trainedDaysThisWeek(data)
        val count = WorkoutStats.workoutsThisWeek(data).size
        val streak = WorkoutStats.weekStreak(data)
        val labels = L10n.weekdaySymbols(veryShort = true)
        provideContent { Content(trained, count, streak, labels) }
    }

    @Composable
    private fun Content(trained: List<Boolean>, count: Int, streak: Int, labels: List<String>) {
        val accent = ColorProvider(Color(0xFFFF6B00))
        val muted = ColorProvider(Color(0xFF9E9EA8))
        val raised = ColorProvider(Color(0xFF27272A))
        Column(
            GlanceModifier.fillMaxSize().background(Color(0xFF0E0E10)).cornerRadius(20.dp).padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("LIFTR", style = TextStyle(color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.defaultWeight())
                Text("🔥 $streak", style = TextStyle(color = if (streak > 0) accent else muted, fontSize = 13.sp, fontWeight = FontWeight.Bold))
            }
            Spacer(GlanceModifier.defaultWeight())
            Text("$count", style = TextStyle(color = ColorProvider(Color.White), fontSize = 36.sp, fontWeight = FontWeight.Bold))
            Text(tr("this week", "deze week").uppercase(), style = TextStyle(color = muted, fontSize = 11.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                for (i in 0 until 7) {
                    Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(GlanceModifier.size(12.dp).cornerRadius(6.dp).background(if (trained.getOrNull(i) == true) accent else raised)) {}
                        Spacer(GlanceModifier.height(2.dp))
                        Text(labels.getOrElse(i) { "" }, style = TextStyle(color = muted, fontSize = 9.sp, fontWeight = FontWeight.Bold))
                    }
                    if (i < 6) Spacer(GlanceModifier.width(2.dp))
                }
            }
        }
    }
}

class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}
