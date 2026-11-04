package nl.lrvdlinden.liftr.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.launch
import nl.lrvdlinden.liftr.BuildConfig
import nl.lrvdlinden.liftr.LiftrApp
import nl.lrvdlinden.liftr.MainActivity
import nl.lrvdlinden.liftr.R
import nl.lrvdlinden.liftr.core.CsvExport
import nl.lrvdlinden.liftr.core.L10n
import nl.lrvdlinden.liftr.core.LiftrData
import nl.lrvdlinden.liftr.core.LiftrFormat
import nl.lrvdlinden.liftr.core.MoreAppsText
import nl.lrvdlinden.liftr.core.tr
import nl.lrvdlinden.liftr.core.trf
import nl.lrvdlinden.liftr.data.Settings
import nl.lrvdlinden.liftr.health.HealthConnectManager
import nl.lrvdlinden.liftr.reminders.ReminderScheduler
import nl.lrvdlinden.liftr.wear.PhoneWearSync
import java.io.File
import java.time.LocalDate

@Composable
fun SettingsScreen(data: LiftrData) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun replan() = scope.launch { ReminderScheduler.reschedule(context) }
    fun reminderToggled(on: Boolean) {
        if (on) MainActivity.current.value?.askNotifications()
        replan()
    }
    val permissionLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        HealthConnectManager.markRequested()
        scope.launch { HealthConnectManager.refresh(context) }
    }
    LiftrPage(title = tr("Settings", "Instellingen")) {
        // Profile
        CardSection(tr("Profile", "Profiel"), tr("Used to greet you and on the back of your badges.",
            "Wordt gebruikt om je te begroeten en op de achterkant van je badges.")) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.AccountCircle, null, tint = LiftrColors.accent, modifier = Modifier.size(28.dp))
                LiftrTextField(Settings.userName, { Settings.setUserName(it) }, tr("Your name", "Je naam"))
            }
        }

        // Training
        CardSection(tr("Training", "Training")) {
            Text(tr("Unit", "Eenheid"), color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
            Box(Modifier.padding(12.dp)) {
                Segmented(listOf(tr("Kilograms (kg)", "Kilogram (kg)"), tr("Pounds (lb)", "Pond (lb)")), if (Settings.unitRaw == "lb") 1 else 0) {
                    Settings.setUnit(if (it == 1) "lb" else "kg")
                    scope.launch { PhoneWearSync.push(context) }
                }
            }
            Divider()
            StepperRow(tr("Rest time", "Rusttijd"), LiftrFormat.duration(Settings.restDefault.toLong()),
                onMinus = { Settings.setRestDefault(maxOf(15, Settings.restDefault - 15)) },
                onPlus = { Settings.setRestDefault(minOf(600, Settings.restDefault + 15)) })
            Divider()
            RowItem(tr("Start rest timer after each set", "Rusttimer starten na elke set"), trailing = {
                Switch(Settings.restAuto, { Settings.setRestAuto(it) }, colors = switchColors())
            })
        }

        // Reminders
        CardSection(tr("Reminders", "Herinneringen"), tr("Reminders are planned on this device. A day you already trained is skipped.",
            "Herinneringen worden op dit apparaat ingepland. Een dag waarop je al hebt getraind wordt overgeslagen.")) {
            RowItem(tr("Training days", "Vaste trainingsdagen"), trailing = {
                Switch(Settings.remindDays, { Settings.setRemindDays(it); reminderToggled(it) }, colors = switchColors())
            })
            if (Settings.remindDays) {
                val order = listOf(2, 3, 4, 5, 6, 7, 1)
                val labels = L10n.weekdaySymbols(veryShort = false)
                val selected = Settings.remindDaysRaw.split(",").mapNotNull { it.toIntOrNull() }.toSet()
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    order.forEachIndexed { i, day ->
                        val on = day in selected
                        Text(labels[i], Modifier.weight(1f).heightIn(min = 36.dp).clip(RoundedCornerShape(10.dp))
                            .background(gradientOrSolid(on, LiftrColors.cardRaised))
                            .clickable {
                                val next = if (on) selected - day else selected + day
                                Settings.setRemindDaysRaw(next.sorted().joinToString(","))
                                replan()
                            }.padding(vertical = 8.dp),
                            color = if (on) Color.White else LiftrColors.muted, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            if (Settings.remindDays || Settings.remindInactive) {
                Divider()
                val t = Settings.remindTime
                RowItem(tr("Time", "Tijdstip"), trailing = {
                    Text(String.format("%02d:%02d", t / 60, t % 60), color = LiftrColors.accent, fontWeight = FontWeight.Bold)
                }) {
                    TimePickerDialog(context, { _, h, m -> Settings.setRemindTime(h * 60 + m); replan() }, t / 60, t % 60, true).show()
                }
            }
            Divider()
            RowItem(tr("When I haven't trained for a while", "Als ik een tijd niet heb getraind"), trailing = {
                Switch(Settings.remindInactive, { Settings.setRemindInactive(it); reminderToggled(it) }, colors = switchColors())
            })
            if (Settings.remindInactive) {
                StepperRow(tr("After", "Na"), trf("{1} days", "{1} dagen", Settings.inactiveDays),
                    onMinus = { Settings.setInactiveDays(maxOf(2, Settings.inactiveDays - 1)); replan() },
                    onPlus = { Settings.setInactiveDays(minOf(14, Settings.inactiveDays + 1)); replan() })
            }
            Divider()
            RowItem(tr("Weekly summary (Sunday 20:00)", "Weekoverzicht (zondag 20:00)"), trailing = {
                Switch(Settings.remindWeekly, { Settings.setRemindWeekly(it); reminderToggled(it) }, colors = switchColors())
            })
        }

        // Health Connect
        CardSection("Health Connect", tr("Liftr saves workouts with duration and calories and reads your weight, body composition and resting heart rate. Change access in Health Connect → App permissions → Liftr.",
            "Liftr bewaart trainingen met duur en calorieën en leest je gewicht, lichaamssamenstelling en rusthartslag. Toegang wijzig je in Health Connect → App-rechten → Liftr.")) {
            if (!HealthConnectManager.isAvailable) {
                RowItem(tr("Health Connect is not available on this phone.", "Health Connect is niet beschikbaar op deze telefoon."), color = LiftrColors.muted)
                Divider()
                RowItem(tr("Install Health Connect", "Health Connect installeren"), color = LiftrColors.accent) {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")))
                    } catch (_: Exception) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")))
                    }
                }
            } else {
                RowItem(if (HealthConnectManager.hasAccess) tr("Connected", "Gekoppeld") else tr("Not connected", "Niet gekoppeld"),
                    icon = Icons.Filled.Favorite, iconTint = LiftrColors.pink, trailing = {
                        TextButton(onClick = { try { permissionLauncher.launch(HealthConnectManager.permissions) } catch (_: Exception) {} }) {
                            Text(if (HealthConnectManager.hasRequested) tr("Check access", "Toegang controleren") else tr("Connect", "Koppelen"),
                                color = LiftrColors.accent)
                        }
                    })
                Divider()
                RowItem(tr("Save workouts", "Trainingen opslaan"), trailing = { Switch(Settings.saveWorkouts, { Settings.setSaveWorkouts(it) }, colors = switchColors()) })
                Divider()
                RowItem(tr("Save weight and body fat", "Gewicht en vetpercentage opslaan"), trailing = { Switch(Settings.saveBody, { Settings.setSaveBody(it) }, colors = switchColors()) })
            }
            Divider()
            RowItem(tr("Log weight", "Gewicht invoeren"), color = LiftrColors.accent) { Nav.push(Route.BodyEntry) }
        }

        // Data
        CardSection(tr("Data", "Gegevens"), tr("Your workouts are stored on this phone only. Export them now and then as a backup.",
            "Je trainingen staan alleen op deze telefoon. Exporteer ze af en toe als back-up.")) {
            RowItem(tr("Import", "Importeren"), icon = Icons.Filled.FileDownload, trailing = { Chevron() }) { Nav.push(Route.Import) }
            Divider()
            RowItem(tr("Export to CSV", "Exporteren als CSV"), icon = Icons.Filled.FileUpload) { shareCsv(context, data) }
            Divider()
            RowItem(tr("Manage data", "Gegevens beheren"), icon = Icons.Filled.DeleteSweep, trailing = { Chevron() }) { Nav.push(Route.DataManagement) }
        }

        CardSection {
            RowItem(tr("Privacy", "Privacy"), icon = Icons.Filled.PanTool, trailing = { Chevron() }) { Nav.push(Route.Privacy) }
            Divider()
            RowItem(tr("Notifications", "Meldingen"), icon = Icons.Filled.Notifications, trailing = { Chevron() }) {
                context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(MoreAppsText.current.section.uppercase(), color = LiftrColors.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp))
            MoreAppsCard()
        }
        AppInfoFooter()
    }
}

@Composable
private fun Chevron() = Icon(Icons.Filled.ChevronRight, null, tint = LiftrColors.muted)

private fun shareCsv(context: android.content.Context, data: LiftrData) {
    val dir = File(context.cacheDir, "export").apply { mkdirs() }
    val file = File(dir, "Liftr-export.csv")
    file.writeText(CsvExport.csv(data))
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, tr("Liftr export", "Liftr-export")).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, tr("Export to CSV", "Exporteren als CSV")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

// MARK: - More apps card (MoreAppsCard.swift)

private object Brand {
    const val URL = "https://lrvdlinden.app"
    val accent = Color(0xFF8A9DFF)
    val onAccent = Color(0xFF0D1018)
    val muted = Color(0xFF98A0B3)
    val surface = Color(0xFF161B27)
    val line = Color(0xFF262D3D)
    val logoTile = Color(0xFF121726)
    val logoDot = Color(0xFF4C6BFF)
}

/** The "LR" mark from lrvdlinden.app, drawn natively (512 × 512 design grid). */
@Composable
fun LRLogo(size: Dp = 60.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width / 512f
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        drawRoundRect(Brand.logoTile, cornerRadius = androidx.compose.ui.geometry.CornerRadius(116 * s))
        val letters = Path().apply {
            moveTo(132 * s, 146 * s); lineTo(132 * s, 370 * s); lineTo(232 * s, 370 * s)
            moveTo(292 * s, 370 * s); lineTo(292 * s, 146 * s); lineTo(340 * s, 146 * s)
            arcTo(androidx.compose.ui.geometry.Rect(p(278f, 146f), Size(124 * s, 124 * s)), -90f, 180f, false)
            lineTo(292 * s, 270 * s)
            moveTo(338 * s, 270 * s); lineTo(390 * s, 370 * s)
        }
        drawPath(letters, Color.White, style = Stroke(46 * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(Brand.logoTile, 41 * s, p(404f, 126f))
        drawCircle(Brand.logoDot, 34 * s, p(404f, 126f))
    }
}

@Composable
fun MoreAppsCard() {
    val context = LocalContext.current
    val text = MoreAppsText.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Brand.surface).border(1.dp, Brand.line, shape)
            .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Brand.URL))) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LRLogo(60.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text.title, color = Brand.accent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(text.subtitle, color = Brand.muted, fontSize = 15.sp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text.action, color = Brand.onAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Brand.accent).padding(horizontal = 14.dp, vertical = 6.dp))
            Text("lrvdlinden.app", color = Brand.muted, fontSize = 11.sp, maxLines = 1)
        }
    }
}

/** App icon, "Versie 1.0.0 (1)" and "© 2026 Liftr" at the very bottom of Settings. */
@Composable
fun AppInfoFooter() {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp))) {
            Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
        }
        Text("${MoreAppsText.current.version}: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = LiftrColors.muted, fontSize = 15.sp)
        Text("© ${LocalDate.now().year} Liftr", color = LiftrColors.muted, fontSize = 15.sp)
    }
}

