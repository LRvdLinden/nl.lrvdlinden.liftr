package nl.lrvdlinden.liftr.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.lrvdlinden.liftr.LiftrApp
import nl.lrvdlinden.liftr.core.BarcodeFormat
import nl.lrvdlinden.liftr.core.BarcodeRenderer
import nl.lrvdlinden.liftr.core.GymPass
import nl.lrvdlinden.liftr.core.GymTemplate
import nl.lrvdlinden.liftr.core.GymTemplates
import nl.lrvdlinden.liftr.core.LiftrData
import nl.lrvdlinden.liftr.core.groupCode
import nl.lrvdlinden.liftr.core.tr
import nl.lrvdlinden.liftr.core.trf
import nl.lrvdlinden.liftr.gym.GymLogoService
import nl.lrvdlinden.liftr.wear.PhoneWearSync

/** What a pass looks like; a value type so the editor can preview unsaved changes. */
data class GymPassLook(
    val name: String,
    val templateId: String?,
    val colorHex: String,
    val code: String,
    val format: BarcodeFormat,
    val memberName: String,
) {
    constructor(pass: GymPass) : this(pass.displayName, pass.templateId, pass.colorHex, pass.code, pass.format, pass.memberName)

    val template: GymTemplate? get() = GymTemplates.template(templateId)
    val color: Color get() = Color(GymTemplates.argb(colorHex))
    val ink: Color get() = if (GymTemplates.inkIsBlack(colorHex)) Color.Black else Color.White
    val monogram: String get() = name.split(" ").filter { it.isNotEmpty() }.take(2).map { it.first().uppercaseChar() }.joinToString("")
}

fun hexColor(hex: String): Color = Color(GymTemplates.argb(hex))

/** The gym's logo on its card: the chain's icon as a rounded tile, a wordmark on a pill, or a monogram for other gyms. */
@Composable
fun GymLogoMark(look: GymPassLook, height: Dp) {
    val context = LocalContext.current
    val version = GymLogoService.version.intValue
    val template = look.template
    val logo = remember(look.templateId, version) { GymTemplates.logo(context, template) }
    LaunchedEffect(look.templateId) { template?.let { GymLogoService.refresh(context, it) } }
    if (logo != null && template != null) {
        val (bitmap, fromWebsite) = logo
        val tile = if (fromWebsite) Color.White else template.logoBackgroundHex?.let { hexColor(it) } ?: Color.Transparent
        if (fromWebsite || !template.wordmark) {
            Box(Modifier.size(height).clip(RoundedCornerShape(height * 0.22f)).background(tile)
                .padding(if (fromWebsite || template.logoBackgroundHex == null) 0.dp else height * 0.1f)) {
                Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        } else {
            Box(Modifier.heightIn(max = height).widthIn(max = height * 3.4f).clip(RoundedCornerShape(height * 0.22f)).background(tile)
                .padding(horizontal = if (template.logoBackgroundHex == null) 0.dp else height * 0.22f,
                    vertical = if (template.logoBackgroundHex == null) 0.dp else height * 0.14f)) {
                Image(bitmap.asImageBitmap(), null, Modifier.height(height), contentScale = ContentScale.Fit, alignment = Alignment.CenterStart)
            }
        }
    } else {
        Box(Modifier.size(height).background(look.ink.copy(alpha = 0.16f), RoundedCornerShape(height * 0.24f)), contentAlignment = Alignment.Center) {
            Text(look.monogram.ifEmpty { "L" }, color = look.ink, fontSize = (height.value * 0.42f).sp, fontWeight = FontWeight.Black)
        }
    }
}

/** True when the logo already shows the gym's name, so the card does not repeat it. */
@Composable
private fun logoShowsName(look: GymPassLook): Boolean {
    val context = LocalContext.current
    val t = look.template ?: return false
    val logo = remember(look.templateId, GymLogoService.version.intValue) { GymTemplates.logo(context, t) } ?: return false
    return !logo.second && t.wordmark
}

/** Draws the code (ZXing) black on white. */
@Composable
fun BarcodeImage(value: String, format: BarcodeFormat, modifier: Modifier = Modifier) {
    val bitmap = remember(value, format) {
        if (format.isLinear) BarcodeRenderer.render(value, format, 900, 320) else BarcodeRenderer.render(value, format, 800, 800)
    }
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), "${format.displayName} code", modifier, contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
    } else {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Filled.QrCodeScanner, null, tint = Color.Gray)
            Text(tr("This code can't be drawn", "Deze code kan niet worden getekend"), color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

/** Front: logo, gym name, member and number. Back (showsCode): the code on a white panel. */
@Composable
fun GymCardFace(look: GymPassLook, showsCode: Boolean, modifier: Modifier = Modifier, compact: Boolean = false) {
    val shape = RoundedCornerShape(24.dp)
    Box(modifier.shadow(14.dp, shape).clip(shape).background(look.color)) {
        Column(Modifier.fillMaxSize().padding(if (compact) 14.dp else 18.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GymLogoMark(look, if (compact) 34.dp else 44.dp)
                if (!logoShowsName(look)) {
                    Text(look.name, color = look.ink, fontSize = if (compact) 17.sp else 20.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                Spacer(Modifier.weight(1f))
                Text(tr("Gym pass", "Sportschoolpas").uppercase(), color = look.ink.copy(alpha = 0.72f), fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
            }
            if (showsCode) {
                Column(Modifier.fillMaxWidth().weight(1f).background(Color.White, RoundedCornerShape(16.dp)).padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BarcodeImage(look.code, look.format, Modifier.fillMaxWidth().weight(1f))
                    Text(groupCode(look.code), color = Color.Black.copy(alpha = 0.75f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace, maxLines = 1)
                }
            } else {
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (look.memberName.isNotEmpty()) Text(look.memberName, color = look.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(groupCode(look.code), color = look.ink.copy(alpha = 0.9f), fontSize = 15.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                    }
                    Icon(Icons.Filled.Sync, null, tint = look.ink.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** Turns around its vertical axis: the front first, the code once the angle passes 90°. */
@Composable
fun FlipGymCard(look: GymPassLook, angle: Float, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val back = (angle % 360f) >= 90f && (angle % 360f) < 270f
    Box(modifier.graphicsLayer { rotationY = angle; cameraDistance = 14f * density }) {
        if (!back) GymCardFace(look, false, Modifier.fillMaxSize())
        else GymCardFace(look, true, Modifier.fillMaxSize().graphicsLayer { rotationY = 180f })
    }
}

/** Round button at the top right of Today, in the colour of the first pass (with the number of passes when there are more). */
@Composable
fun GymPassButton(data: LiftrData) {
    val passes = data.passesSorted
    val first = passes.firstOrNull()
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(50.dp).then(if (first != null) Modifier.shadow(10.dp, CircleShape, spotColor = hexColor(first.colorHex)) else Modifier)
                .clip(CircleShape).background(if (first != null) hexColor(first.colorHex) else LiftrColors.card)
                .border(if (first != null) 1.dp else 1.5.dp, if (first != null) Color.White.copy(alpha = 0.18f) else LiftrColors.accent.copy(alpha = 0.45f), CircleShape)
                .clickable { if (passes.isEmpty()) Nav.push(Route.PassEdit(null)) else Nav.push(Route.Wallet) },
            contentAlignment = Alignment.Center,
        ) {
            if (first != null) {
                Icon(Icons.Filled.AccountBalanceWallet, tr("My passes", "Mijn passen"),
                    tint = if (GymTemplates.inkIsBlack(first.colorHex)) Color.Black else Color.White, modifier = Modifier.size(24.dp))
            } else {
                Icon(Icons.Outlined.AccountBalanceWallet, tr("Add your gym pass", "Voeg je sportschoolpas toe"), tint = LiftrColors.accent, modifier = Modifier.size(24.dp))
            }
        }
        if (passes.size > 1) {
            Text("${passes.size}", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape).background(Color.White))
        }
    }
}

/** All passes as cards: tap one to show its code, long-press to edit or delete, + to add. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WalletScreen(data: LiftrData) {
    var menuFor by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<GymPass?>(null) }
    DetailScaffold(tr("My passes", "Mijn passen"), onBack = { Nav.stack.removeAll { it is Route.Wallet } }, maxWidth = 560.dp, actions = {
        IconButton(onClick = { Nav.push(Route.PassEdit(null)) }) { Icon(Icons.Filled.Add, tr("Add pass", "Pas toevoegen"), tint = LiftrColors.accent) }
    }) {
        for (pass in data.passesSorted) {
            Box {
                GymCardFace(GymPassLook(pass), false, Modifier.fillMaxWidth().aspectRatio(1.75f)
                    .combinedClickable(onClick = { Nav.push(Route.PassFull(pass.id)) }, onLongClick = { menuFor = pass.id }), compact = true)
                DropdownMenu(expanded = menuFor == pass.id, onDismissRequest = { menuFor = null }) {
                    DropdownMenuItem(text = { Text(tr("Show code", "Toon code")) }, leadingIcon = { Icon(Icons.Filled.QrCode, null) },
                        onClick = { menuFor = null; Nav.push(Route.PassFull(pass.id)) })
                    DropdownMenuItem(text = { Text(tr("Edit pass", "Pas wijzigen")) }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = { menuFor = null; Nav.push(Route.PassEdit(pass.id)) })
                    DropdownMenuItem(text = { Text(tr("Delete pass", "Pas verwijderen"), color = LiftrColors.danger) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null, tint = LiftrColors.danger) },
                        onClick = { menuFor = null; deleting = pass })
                }
            }
        }
        LiftrButton(tr("Add pass", "Pas toevoegen"), Modifier.padding(top = 6.dp), filled = data.passes.isEmpty(), icon = Icons.Filled.Add) {
            Nav.push(Route.PassEdit(null))
        }
    }
    deleting?.let { pass ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(tr("Delete this pass?", "Pas verwijderen?")) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    LiftrApp.repository.commit { deletePasses += pass.id }
                    LiftrApp.afterWorkoutChange()
                }) { Text(tr("Delete", "Verwijderen"), color = LiftrColors.danger) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(tr("Cancel", "Annuleren")) } },
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The pass comes forward and turns to its code; screen brightness goes up. Tap the card to turn it back. */
@Composable
fun PassFullScreen(data: LiftrData, id: String) {
    val pass = data.passes[id]
    val context = LocalContext.current
    fun close() { Nav.stack.lastOrNull { it is Route.PassFull }?.let { Nav.remove(it) } }
    BackHandler { close() }
    var target by remember { mutableFloatStateOf(0f) }
    val angle by animateFloatAsState(target, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow), label = "pass")
    LaunchedEffect(Unit) { delay(250); target = 180f }
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        val old = window?.attributes?.screenBrightness
        window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = 1f }; w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        onDispose {
            window?.let { w ->
                w.attributes = w.attributes.apply { screenBrightness = old ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
                w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    if (pass == null) { LaunchedEffect(Unit) { close() }; return }
    val look = GymPassLook(pass)
    Box(Modifier.fillMaxSize().background(Color.Black).background(look.color.copy(alpha = 0.25f)).statusBarsPadding()) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).clickable { close() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, tr("Close", "Sluiten"), tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { Nav.push(Route.PassEdit(pass.id)) }) { Text(tr("Edit", "Wijzig"), color = Color.White, fontSize = 17.sp) }
            }
            Spacer(Modifier.weight(1f))
            FlipGymCard(look, angle, Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(0.82f)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { target = if (target >= 90f) 0f else 180f })
            Text(if (target >= 90f) tr("Tap the card to turn it over", "Tik op de kaart om hem om te draaien")
                else tr("Tap the card to show the code", "Tik op de kaart om de code te tonen"),
                color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(top = 20.dp))
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.WbSunny, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                Text(" " + tr("Screen brightness is turned up", "Schermhelderheid staat op maximaal"), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
    }
}

/** Pick your gym chain (with logo and colour), or another gym. Chain names appear only here and on the pass. */
@Composable
fun GymPickerScreen(onPick: (GymTemplate?) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    val filtered = GymTemplates.selectable.filter { search.isEmpty() || it.name.contains(search, true) }
    fun pick(t: GymTemplate?) {
        Nav.stack.lastOrNull { it is Route.GymPicker }?.let { Nav.remove(it) }
        onPick(t)
    }
    DetailScaffold(tr("Choose your gym", "Kies je sportschool"), onBack = { Nav.stack.lastOrNull { it is Route.GymPicker }?.let { Nav.remove(it) } }) {
        SearchField(search, { search = it }, tr("Search gym", "Zoek sportschool"))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = maxOf(2, (maxWidth.value / 162).toInt())
            val tiles: List<GymTemplate?> = filtered + listOf(null)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (row in tiles.chunked(columns)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (t in row) {
                            if (t != null) {
                                val look = GymPassLook(t.name, t.id, t.colorHex, "", t.format, "")
                                Column(Modifier.weight(1f).heightIn(min = 96.dp).clip(RoundedCornerShape(18.dp)).background(look.color)
                                    .clickable { pick(t) }.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                    GymLogoMark(look, 36.dp)
                                    Text(t.name, color = look.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(top = 8.dp))
                                }
                            } else {
                                Column(Modifier.weight(1f).heightIn(min = 96.dp).clip(RoundedCornerShape(18.dp)).background(LiftrColors.card)
                                    .border(1.dp, LiftrColors.line, RoundedCornerShape(18.dp)).clickable { pick(null) }.padding(14.dp),
                                    verticalArrangement = Arrangement.SpaceBetween) {
                                    Icon(Icons.Filled.Add, null, tint = LiftrColors.accent, modifier = Modifier.size(28.dp))
                                    Text(tr("Another gym", "Andere sportschool"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 8.dp))
                                }
                            }
                        }
                        repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

private val palette = listOf(
    "#FF6B00", "#E30613", "#C2185B", "#7B1FA2", "#5E2D91", "#303F9F", "#1976D2", "#0288D1",
    "#00897B", "#2E7D32", "#8DC21F", "#F7B72C", "#FBC02D", "#6D4C41", "#455A64", "#111111",
)

/** Add or edit a pass: choose the gym, then scan the card, read a screenshot, or type the number. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PassEditScreen(data: LiftrData, id: String?) {
    val pass = id?.let { data.passes[it] }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var templateId by rememberSaveable { mutableStateOf(pass?.templateId) }
    var gymName by rememberSaveable { mutableStateOf(if (pass?.templateId == null) pass?.gymName ?: "" else "") }
    var colorHex by rememberSaveable { mutableStateOf(pass?.colorHex ?: "#FF6B00") }
    var memberName by rememberSaveable { mutableStateOf(pass?.memberName ?: "") }
    var code by rememberSaveable { mutableStateOf(pass?.code ?: "") }
    var formatRaw by rememberSaveable { mutableStateOf(pass?.formatRaw ?: BarcodeFormat.QR.raw) }
    var note by rememberSaveable { mutableStateOf(pass?.note ?: "") }
    var previewBack by rememberSaveable { mutableStateOf(false) }
    var photoError by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var formatMenu by remember { mutableStateOf(false) }
    var askedPicker by rememberSaveable { mutableStateOf(false) }
    val template = GymTemplates.template(templateId)
    val trimmed = code.trim()
    fun close() { Nav.stack.lastOrNull { it is Route.PassEdit }?.let { Nav.remove(it) } }
    fun openPicker() {
        Nav.push(Route.GymPicker { picked ->
            templateId = picked?.id
            if (picked != null) {
                gymName = ""
                colorHex = picked.colorHex
                if (code.isBlank()) formatRaw = picked.format.raw
            }
        })
    }
    LaunchedEffect(Unit) { if (pass == null && !askedPicker) { askedPicker = true; openPicker() } }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val result = CodeReader.readImage(context, uri)
            if (result != null) { code = result.first; formatRaw = result.second.raw; previewBack = true } else photoError = true
        }
    }
    val look = GymPassLook(gymName.trim().ifEmpty { template?.name ?: tr("Gym", "Sportschool") }, templateId, colorHex,
        trimmed.ifEmpty { "0000 0000 0000" }, BarcodeFormat.from(formatRaw), memberName)
    val angle by animateFloatAsState(if (previewBack && trimmed.isNotEmpty()) 180f else 0f, spring(dampingRatio = 0.85f), label = "preview")

    fun save() {
        val target = (pass ?: GymPass()).copy(
            templateId = templateId, gymName = if (template == null) gymName.trim() else "", colorHex = colorHex,
            memberName = memberName.trim(), code = trimmed, formatRaw = formatRaw, note = note,
        )
        LiftrApp.repository.commit { passes += target }
        scope.launch { PhoneWearSync.push(LiftrApp.instance) }
        close()
    }

    DetailScaffold(if (pass == null) tr("Gym pass", "Sportschoolpas") else tr("Edit pass", "Pas wijzigen"), onBack = { close() }, maxWidth = 600.dp, actions = {
        TextButton(onClick = ::save, enabled = trimmed.isNotEmpty()) { Text(tr("Save", "Bewaar"), color = LiftrColors.accent, fontWeight = FontWeight.Bold) }
    }) {
        FlipGymCard(look, angle, Modifier.fillMaxWidth().aspectRatio(if (previewBack && trimmed.isNotEmpty()) 0.95f else 1.9f)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (trimmed.isNotEmpty()) previewBack = !previewBack })

        CardSection(tr("Gym", "Sportschool")) {
            RowItem(template?.name ?: if (gymName.isEmpty()) tr("Choose your gym", "Kies je sportschool") else tr("Another gym", "Andere sportschool"),
                trailing = { Icon(Icons.Filled.Edit, null, tint = LiftrColors.muted, modifier = Modifier.size(18.dp)) }) { openPicker() }
            if (template == null) {
                Divider()
                Box(Modifier.padding(12.dp)) { LiftrTextField(gymName, { gymName = it }, tr("Name of the gym", "Naam van de sportschool")) }
                Text(tr("Card colour", "Kleur van de kaart"), color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(start = 16.dp))
                FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (hex in palette) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(hexColor(hex))
                            .border(if (hex.equals(colorHex, true)) 3.dp else 1.dp, if (hex.equals(colorHex, true)) Color.White else LiftrColors.line, CircleShape)
                            .clickable { colorHex = hex })
                    }
                }
            }
        }

        CardSection(tr("Code", "Code"), tr("Scan the card or key fob, or take a screenshot of the code in your gym's app and choose it here.",
            "Scan je pasje of sleutelhanger, of maak een screenshot van de code in de app van je sportschool en kies hem hier.")) {
            RowItem(tr("Scan with camera", "Scan met de camera"), icon = Icons.Filled.QrCodeScanner, color = LiftrColors.accent) {
                Nav.push(Route.Scanner { value, format -> code = value; formatRaw = format.raw; previewBack = true })
            }
            Divider()
            RowItem(tr("Choose a screenshot or photo", "Kies een screenshot of foto"), icon = Icons.Filled.Photo, color = LiftrColors.accent) {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            Divider()
            Box(Modifier.padding(12.dp)) {
                LiftrTextField(code, { code = it }, tr("Code or member number", "Code of lidnummer"), monospace = true)
            }
            Box {
                RowItem(tr("Type", "Soort"), trailing = { Text(BarcodeFormat.from(formatRaw).displayName, color = LiftrColors.accent) }) { formatMenu = true }
                DropdownMenu(expanded = formatMenu, onDismissRequest = { formatMenu = false }) {
                    for (f in BarcodeFormat.entries) DropdownMenuItem(text = { Text(f.displayName) }, onClick = { formatRaw = f.raw; formatMenu = false })
                }
            }
        }

        CardSection(tr("Details", "Gegevens")) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LiftrTextField(memberName, { memberName = it }, tr("Name on the pass (optional)", "Naam op de pas (optioneel)"))
                LiftrTextField(note, { note = it }, tr("Note (optional)", "Notitie (optioneel)"), singleLine = false, minLines = 2)
            }
        }
        if (pass != null) {
            CardSection {
                RowItem(tr("Delete pass", "Pas verwijderen"), color = LiftrColors.danger, icon = Icons.Filled.Delete, iconTint = LiftrColors.danger) { confirmDelete = true }
            }
        }
    }
    if (photoError) {
        AlertDialog(onDismissRequest = { photoError = false }, title = { Text(tr("No code found", "Geen code gevonden")) },
            text = { Text(tr("Liftr couldn't find a QR code or barcode in this picture.", "Liftr vond geen QR- of barcode in deze afbeelding.")) },
            confirmButton = { TextButton(onClick = { photoError = false }) { Text("OK") } })
    }
    if (confirmDelete && pass != null) {
        AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(tr("Delete this pass?", "Pas verwijderen?")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    close()
                    Nav.stack.removeAll { it is Route.PassFull }
                    LiftrApp.repository.commit { deletePasses += pass.id }
                    scope.launch { PhoneWearSync.push(LiftrApp.instance) }
                }) { Text(tr("Delete", "Verwijderen"), color = LiftrColors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancel", "Annuleren")) } })
    }
}

