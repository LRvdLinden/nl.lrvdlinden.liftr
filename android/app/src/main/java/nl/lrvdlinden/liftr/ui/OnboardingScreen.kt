package nl.lrvdlinden.liftr.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.launch
import nl.lrvdlinden.liftr.LiftrApp
import nl.lrvdlinden.liftr.core.tr
import nl.lrvdlinden.liftr.data.Settings
import nl.lrvdlinden.liftr.health.HealthConnectManager

private class Page(val icon: ImageVector, val title: String, val text: String)

/** First-launch welcome: a few short pages with your name, ending with the Health Connect connection. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen() {
    val pages = listOf(
        Page(Icons.Filled.FitnessCenter, tr("Every rep counts", "Elke herhaling telt"),
            tr("Log your exercises, sets, reps and weight in seconds — no more notes.",
                "Leg je oefeningen, sets, herhalingen en gewicht vast in een paar tikken. Geen notities meer.")),
        Page(Icons.Filled.Person, tr("What's your name?", "Hoe heet je?"),
            tr("Liftr uses it to greet you and puts it on the back of the badges you earn. You can change it later in Settings.",
                "Liftr gebruikt hem om je te begroeten en zet hem op de achterkant van de badges die je haalt. Je kunt hem later wijzigen in Instellingen.")),
        Page(Icons.Filled.ShowChart, tr("See your progress", "Zie je vooruitgang"),
            tr("Liftr shows what you did last time, your records and how much stronger you're getting.",
                "Liftr laat zien wat je vorige keer deed, je records en hoeveel sterker je wordt.")),
        Page(Icons.Filled.Favorite, tr("Works with Health Connect", "Werkt met Health Connect"),
            tr("Workouts, calories and weight go to Health Connect, so other health apps on your phone see them too.",
                "Trainingen, calorieën en gewicht gaan naar Health Connect, zodat andere gezondheidsapps op je telefoon ze ook zien.")),
    )
    val namePage = 1
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(Settings.userName) }
    val context = LiftrApp.instance
    val healthAvailable = remember { HealthConnectManager.sdkAvailable(context) }
    fun done() {
        Settings.putUserName(name)
        Settings.putOnboardingDone(true)
    }
    val permissionLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        HealthConnectManager.markRequested()
        scope.launch { HealthConnectManager.refresh(context) }
        done()
    }

    Column(Modifier.fillMaxSize().background(LiftrColors.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("LIFTR", style = LiftrType.display(28.sp).copy(color = LiftrColors.accent), modifier = Modifier.weight(1f))
            if (pager.currentPage < pages.size - 1) {
                Text(tr("Skip", "Overslaan"), color = LiftrColors.muted, fontSize = 16.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { scope.launch { pager.animateScrollToPage(pages.size - 1) } }.padding(6.dp))
            }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = 560.dp).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    Box(Modifier.size(190.dp).clip(CircleShape).background(LiftrColors.accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(130.dp).clip(CircleShape).background(LiftrColors.accentGradient), contentAlignment = Alignment.Center) {
                            Icon(pages[i].icon, null, tint = Color.White, modifier = Modifier.size(60.dp))
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(pages[i].title.uppercase(), style = LiftrType.display(38.sp), textAlign = TextAlign.Center)
                        Text(pages[i].text, color = LiftrColors.muted, fontSize = 19.sp, textAlign = TextAlign.Center, lineHeight = 25.sp)
                        if (i == namePage) {
                            BasicTextField(
                                value = name, onValueChange = { name = it }, singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                                cursorBrush = SolidColor(LiftrColors.accent),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, autoCorrect = false),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                decorationBox = { inner ->
                                    Box(Modifier.fillMaxWidth().background(LiftrColors.card, RoundedCornerShape(16.dp))
                                        .border(1.5.dp, LiftrColors.line, RoundedCornerShape(16.dp)).padding(vertical = 14.dp, horizontal = 16.dp),
                                        contentAlignment = Alignment.Center) {
                                        if (name.isEmpty()) Text(tr("Your name", "Je naam"), color = LiftrColors.muted, fontSize = 22.sp)
                                        inner()
                                    }
                                },
                            )
                        }
                        if (i == pages.size - 1 && !healthAvailable) {
                            Text(tr("Health Connect is not available on this phone. Liftr works fine without it.",
                                "Health Connect is niet beschikbaar op deze telefoon. Liftr werkt ook zonder."),
                                color = LiftrColors.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) {
            for (i in pages.indices) {
                Box(Modifier.padding(4.dp).size(8.dp).clip(CircleShape).background(if (i == pager.currentPage) Color.White else LiftrColors.cardRaised))
            }
        }
        Column(Modifier.widthIn(max = 480.dp).align(Alignment.CenterHorizontally).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (pager.currentPage == pages.size - 1) {
                if (healthAvailable) {
                    LiftrButton(tr("Connect Health Connect", "Koppel Health Connect")) {
                        try { permissionLauncher.launch(HealthConnectManager.permissions) } catch (e: Exception) { done() }
                    }
                    LiftrButton(tr("Not now", "Nu niet"), filled = false) { done() }
                } else {
                    LiftrButton(tr("Start", "Start")) { done() }
                }
            } else {
                val emptyName = pager.currentPage == namePage && name.isBlank()
                LiftrButton(if (emptyName) tr("Skip for now", "Later invullen") else tr("Next", "Volgende"), filled = !emptyName) {
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }
            }
        }
    }
}
