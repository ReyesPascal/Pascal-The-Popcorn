package com.arflix.tv.ui.screens.tvcheck

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.arflix.tv.R
import com.arflix.tv.util.PictureFormat
import com.arflix.tv.util.TvCapabilities
import com.arflix.tv.util.TvOverrides
import com.arflix.tv.util.TvOverridesStore
import com.arflix.tv.util.TvProfile
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import java.util.Locale

/*
 * Personal build 1.0.1: first-run TV check (PLAN-1.0.1.md section 3).
 * Welcome -> Scanning -> Results (-> Edit). Also opened from Settings -> Playback -> This TV,
 * which starts on Results. Every value shown is the effective profile (scan + owner corrections);
 * the scan's own value decides whether an item is marked "set by you".
 */

private val ScreenBackground = Color(0xFF0E0F12)
private val Dim = Color.White.copy(alpha = 0.6f)
private val Faint = Color.White.copy(alpha = 0.38f)
private val SwitchOn = Color(0xFF3F8F5F)

private enum class Step { WELCOME, SCANNING, RESULTS, EDIT }

/**
 * @param fromSettings opened from Settings: starts on Results and [onBack] returns to Settings.
 * @param onDone "Looks right" (the intro is already marked done when this is called).
 */
@Composable
fun TvCheckScreen(
    fromSettings: Boolean,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { TvOverridesStore.effective(context) }
    val effective by TvOverridesStore.effectiveFlow.collectAsState()
    // The raw scan, re-read whenever the effective profile changes (scan, re-scan, save, reset).
    val scan = remember(effective) { TvCapabilities.cached(context) }
    var saved by remember { mutableStateOf(TvOverridesStore.overrides(context)) }
    var draft by remember { mutableStateOf(saved) }

    var step by rememberSaveable {
        mutableStateOf(
            when {
                !fromSettings -> Step.WELCOME
                TvCapabilities.cached(context) == null -> Step.SCANNING
                else -> Step.RESULTS
            }
        )
    }
    // Where to go once a scan finishes ("Scan again" in Edit returns to Edit).
    var afterScan by rememberSaveable { mutableStateOf(Step.RESULTS) }

    // Edit discards unsaved changes on Back; the first-run flow has no skip, so Back does nothing
    // after Welcome (Back on Welcome leaves the app as usual).
    BackHandler(enabled = step == Step.EDIT) {
        draft = saved
        step = Step.RESULTS
    }
    BackHandler(enabled = !fromSettings && (step == Step.SCANNING || step == Step.RESULTS)) { }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground),
        contentAlignment = Alignment.Center
    ) {
        when (step) {
            Step.WELCOME -> WelcomeStep(onScan = {
                afterScan = Step.RESULTS
                step = Step.SCANNING
            })

            Step.SCANNING -> ScanningStep(onFinished = { ok ->
                saved = TvOverridesStore.overrides(context)
                // A failed scan with nothing saved goes back to Welcome, which offers "Scan this TV" again.
                step = if (ok || TvCapabilities.cached(context) != null) afterScan else Step.WELCOME
            })

            Step.RESULTS -> {
                val profile = effective
                if (scan == null || profile == null) {
                    // Nothing scanned (only reachable if a scan failed); scan first.
                    LaunchedEffect(Unit) { step = Step.SCANNING }
                } else {
                    ResultsStep(
                        scan = scan,
                        profile = profile,
                        overrides = saved,
                        onPreferredFormat = { format ->
                            val updated = saved.copy(preferredFormat = format)
                            TvOverridesStore.save(context, updated)
                            saved = updated
                            draft = updated
                        },
                        onLooksRight = {
                            TvOverridesStore.markIntroDone(context)
                            onDone()
                        },
                        onSomethingWrong = {
                            draft = saved
                            step = Step.EDIT
                        }
                    )
                }
            }

            Step.EDIT -> {
                if (scan == null) {
                    LaunchedEffect(Unit) { step = Step.SCANNING }
                } else {
                    EditStep(
                        scan = scan,
                        draft = draft,
                        onDraftChange = { draft = it },
                        onSave = {
                            val applied = TvOverridesStore.applyOverrides(scan, draft)
                            val format = draft.preferredFormat
                                .takeIf { it in TvOverridesStore.supportedFormats(applied) }
                                ?: PictureFormat.BEST_FOR_TV
                            val toSave = draft.copy(preferredFormat = format)
                            TvOverridesStore.save(context, toSave)
                            TvOverridesStore.markIntroDone(context)
                            saved = toSave
                            draft = toSave
                            step = Step.RESULTS
                        },
                        onReset = {
                            TvOverridesStore.resetCorrections(context)
                            saved = TvOverridesStore.overrides(context)
                            draft = saved
                        },
                        onScanAgain = {
                            afterScan = Step.EDIT
                            step = Step.SCANNING
                        }
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ steps

@Composable
private fun WelcomeStep(onScan: () -> Unit) {
    val focus = remember { FocusRequester() }
    RequestInitialFocus(focus)
    Column(
        modifier = Modifier.widthIn(max = 680.dp).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.arvio_loading_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(150.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.tv_check_app_name),
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.tv_check_welcome_body),
            color = Dim,
            fontSize = 19.sp,
            lineHeight = 27.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(36.dp))
        BigButton(
            text = stringResource(R.string.tv_check_scan_button),
            onClick = onScan,
            modifier = Modifier.focusRequester(focus)
        )
    }
}

@Composable
private fun ScanningStep(onFinished: (ok: Boolean) -> Unit) {
    val context = LocalContext.current
    var ticked by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        val scan = async { runCatching { TvCapabilities.rescan(context) }.isSuccess }
        // At least ~2 s, so each part visibly ticks off: Screen -> HDR -> Video -> Audio.
        repeat(4) {
            delay(550)
            ticked = it + 1
        }
        val ok = scan.await()
        delay(250)
        onFinished(ok)
    }
    val labels = listOf(
        stringResource(R.string.tv_check_screen),
        stringResource(R.string.tv_check_hdr),
        stringResource(R.string.tv_check_video),
        stringResource(R.string.tv_check_audio)
    )
    Column(
        modifier = Modifier.widthIn(max = 520.dp).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.arvio_loading_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(90.dp)
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.tv_check_scanning_title),
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(26.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            labels.forEachIndexed { index, label ->
                val done = index < ticked
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (done) "✓" else "•",
                        color = if (done) Color.White else Faint,
                        fontSize = 22.sp,
                        modifier = Modifier.width(36.dp)
                    )
                    Text(
                        text = label,
                        color = if (done) Color.White else Faint,
                        fontSize = 22.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultsStep(
    scan: TvProfile,
    profile: TvProfile,
    overrides: TvOverrides,
    onPreferredFormat: (PictureFormat) -> Unit,
    onLooksRight: () -> Unit,
    onSomethingWrong: () -> Unit
) {
    val focus = remember { FocusRequester() }
    RequestInitialFocus(focus)
    val setByYou = stringResource(R.string.tv_check_set_by_you)
    StepColumn {
        Title(stringResource(R.string.tv_check_results_title))

        InfoLine(stringResource(R.string.tv_check_screen)) {
            val is4k = profile.screenClass >= 2160
            ValueText(
                text = stringResource(if (is4k) R.string.tv_check_screen_4k else R.string.tv_check_screen_1080p),
                setByYou = corrected(overrides.screenClass?.let { it >= 2160 }, scan.screenClass >= 2160),
                setByYouLabel = setByYou
            )
        }
        InfoLine(stringResource(R.string.tv_check_hdr)) {
            hdrItems(scan, profile, overrides).forEach { item ->
                CheckItem(item.label, item.value, item.setByYou, setByYou)
            }
        }
        InfoLine(stringResource(R.string.tv_check_video)) {
            CheckItem("H.264", profile.avc.hardware, false, setByYou)
            CheckItem("HEVC 10-bit", profile.hevc.hardware && profile.hevc.tenBit, false, setByYou)
            CheckItem("AV1", profile.av1.hardware, false, setByYou)
            CheckItem("VP9", profile.vp9.hardware, false, setByYou)
        }
        InfoLine(stringResource(R.string.tv_check_audio)) {
            val audio = audioFormats(profile)
            ValueText(
                text = audio.ifEmpty { listOf(stringResource(R.string.tv_check_audio_stereo)) }.joinToString("  ·  "),
                setByYou = false,
                setByYouLabel = setByYou
            )
        }

        Spacer(Modifier.height(6.dp))
        PreferredFormatRow(
            profile = profile,
            current = overrides.preferredFormat,
            onChange = onPreferredFormat
        )

        deviceLine(profile).takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(10.dp))
            Text(text = it, color = Faint, fontSize = 14.sp)
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BigButton(
                text = stringResource(R.string.tv_check_looks_right),
                onClick = onLooksRight,
                modifier = Modifier.focusRequester(focus)
            )
            BigButton(text = stringResource(R.string.tv_check_something_wrong), onClick = onSomethingWrong)
        }
    }
}

@Composable
private fun EditStep(
    scan: TvProfile,
    draft: TvOverrides,
    onDraftChange: (TvOverrides) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onScanAgain: () -> Unit
) {
    val focus = remember { FocusRequester() }
    RequestInitialFocus(focus)
    val profile = remember(scan, draft) { TvOverridesStore.applyOverrides(scan, draft) }
    val setByYou = stringResource(R.string.tv_check_set_by_you)
    // A switch that ends up equal to the scan clears the correction, so "set by you" only
    // marks real differences.
    fun flip(current: Boolean, scanValue: Boolean): Boolean? = (!current).takeIf { it != scanValue }

    StepColumn {
        Title(stringResource(R.string.tv_check_edit_title))

        val is4k = profile.screenClass >= 2160
        val scan4k = scan.screenClass >= 2160
        SwitchRow(
            title = stringResource(R.string.tv_check_screen_4k_switch),
            subtitle = stringResource(R.string.tv_check_screen_4k_switch_desc),
            checked = is4k,
            setByYou = corrected(draft.screenClass?.let { it >= 2160 }, scan4k),
            setByYouLabel = setByYou,
            onClick = {
                onDraftChange(draft.copy(screenClass = flip(is4k, scan4k)?.let { if (it) 2160 else 1080 }))
            },
            modifier = Modifier.focusRequester(focus)
        )
        hdrItems(scan, profile, draft).forEach { item ->
            SwitchRow(
                title = item.label,
                subtitle = "",
                checked = item.value,
                setByYou = item.setByYou,
                setByYouLabel = setByYou,
                onClick = { onDraftChange(item.update(draft, flip(item.value, item.scanValue))) }
            )
        }
        PreferredFormatRow(
            profile = profile,
            current = draft.preferredFormat,
            onChange = { onDraftChange(draft.copy(preferredFormat = it)) }
        )

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BigButton(text = stringResource(R.string.tv_check_save), onClick = onSave)
            BigButton(text = stringResource(R.string.tv_check_reset), onClick = onReset)
            BigButton(text = stringResource(R.string.tv_check_scan_again), onClick = onScanAgain)
        }
    }
}

// ------------------------------------------------------------------ data helpers

private class HdrItem(
    val label: String,
    val value: Boolean,
    val scanValue: Boolean,
    val setByYou: Boolean,
    val update: (TvOverrides, Boolean?) -> TvOverrides
)

/** Dolby Vision, HDR10, HDR10+, HLG: effective value, scan value and whether the owner changed it. */
private fun hdrItems(scan: TvProfile, profile: TvProfile, overrides: TvOverrides): List<HdrItem> = listOf(
    HdrItem("Dolby Vision", profile.hasDolbyVision, scan.hasDolbyVision,
        corrected(overrides.dolbyVision, scan.hasDolbyVision)) { o, v -> o.copy(dolbyVision = v) },
    HdrItem("HDR10", profile.displayHdr10, scan.displayHdr10,
        corrected(overrides.hdr10, scan.displayHdr10)) { o, v -> o.copy(hdr10 = v) },
    HdrItem("HDR10+", profile.displayHdr10Plus, scan.displayHdr10Plus,
        corrected(overrides.hdr10Plus, scan.displayHdr10Plus)) { o, v -> o.copy(hdr10Plus = v) },
    HdrItem("HLG", profile.displayHlg, scan.displayHlg,
        corrected(overrides.hlg, scan.displayHlg)) { o, v -> o.copy(hlg = v) }
)

private fun corrected(override: Boolean?, scanValue: Boolean): Boolean = override != null && override != scanValue

/** Only the passthrough formats the TV reports. */
private fun audioFormats(p: TvProfile): List<String> = buildList {
    if (p.audioEac3Joc) add("Dolby Atmos")
    if (p.audioEac3) add("Dolby Digital Plus")
    if (p.audioAc3) add("Dolby Digital")
    if (p.audioTrueHd) add("Dolby TrueHD")
    if (p.audioDtsHd) add("DTS-HD")
    if (p.audioDts) add("DTS")
}

/** "Hisense 65U6H · MT9638 · 3.8 GB RAM · Android 11", leaving out anything the scan did not report. */
@Composable
private fun deviceLine(p: TvProfile): String {
    val name = run {
        val maker = p.manufacturer.trim().replaceFirstChar { it.titlecase(Locale.US) }
        val model = p.model.trim()
        when {
            model.isEmpty() -> maker
            maker.isEmpty() || model.startsWith(maker, ignoreCase = true) -> model
            else -> "$maker $model"
        }
    }
    val ram = p.totalRamMb.takeIf { it > 0 }?.let {
        val gb = String.format(Locale.US, "%.1f", it / 1024.0).removeSuffix(".0")
        stringResource(R.string.tv_check_ram_gb, gb)
    }
    val android = androidVersion(p.sdkInt)?.let { stringResource(R.string.tv_check_android_version, it) }
    return listOfNotNull(
        name.takeIf { it.isNotBlank() },
        p.chip.trim().uppercase(Locale.US).takeIf { it.isNotBlank() },
        ram,
        android
    ).joinToString("  ·  ")
}

private fun androidVersion(sdk: Int): String? = when (sdk) {
    21, 22 -> "5"
    23 -> "6"
    24, 25 -> "7"
    26, 27 -> "8"
    28 -> "9"
    29 -> "10"
    30 -> "11"
    31 -> "12"
    32 -> "12L"
    33 -> "13"
    34 -> "14"
    35 -> "15"
    36 -> "16"
    else -> null
}

@Composable
private fun formatLabel(format: PictureFormat): String = stringResource(
    when (format) {
        PictureFormat.BEST_FOR_TV -> R.string.tv_check_format_best
        PictureFormat.DOLBY_VISION -> R.string.tv_check_format_dolby_vision
        PictureFormat.HDR10_PLUS -> R.string.tv_check_format_hdr10_plus
        PictureFormat.HDR10 -> R.string.tv_check_format_hdr10
    }
)

// ------------------------------------------------------------------ building blocks

@Composable
private fun RequestInitialFocus(focus: FocusRequester) {
    LaunchedEffect(focus) {
        delay(80)
        runCatching { focus.requestFocus() }
    }
}

@Composable
private fun StepColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 72.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.Start,
        content = content
    )
}

@Composable
private fun Title(text: String) {
    Text(text = text, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(20.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoLine(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Dim, fontSize = 18.sp, modifier = Modifier.width(110.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(26.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) { content() }
    }
}

@Composable
private fun ValueText(text: String, setByYou: Boolean, setByYouLabel: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = text, color = Color.White, fontSize = 18.sp)
        if (setByYou) SetByYouTag(setByYouLabel)
    }
}

@Composable
private fun CheckItem(label: String, value: Boolean, setByYou: Boolean, setByYouLabel: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (value) "✓ $label" else "✗ $label",
            color = if (value) Color.White else Faint,
            fontSize = 18.sp
        )
        if (setByYou) SetByYouTag(setByYouLabel)
    }
}

@Composable
private fun SetByYouTag(label: String) {
    Text(
        text = "($label)",
        color = Dim,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 6.dp)
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FocusRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.06f),
            contentColor = Color.White,
            focusedContainerColor = Color.White.copy(alpha = 0.16f),
            focusedContentColor = Color.White,
            pressedContainerColor = Color.White.copy(alpha = 0.22f),
            pressedContentColor = Color.White
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(3.dp, Color.White),
                shape = shape
            )
        )
    ) {
        Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) { content() }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    setByYou: Boolean,
    setByYouLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FocusRow(onClick = onClick, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = title, fontSize = 19.sp)
                    if (setByYou) SetByYouTag(setByYouLabel)
                }
                if (subtitle.isNotEmpty()) Text(text = subtitle, color = Dim, fontSize = 14.sp)
            }
            Spacer(Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .width(50.dp)
                    .height(28.dp)
                    .background(
                        if (checked) SwitchOn else Color.White.copy(alpha = 0.22f),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(4.dp),
                contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(Modifier.size(20.dp).background(Color.White, RoundedCornerShape(10.dp)))
            }
        }
    }
}

/** Click cycles through the formats this TV supports (always starting with "Best for this TV"). */
@Composable
private fun PreferredFormatRow(
    profile: TvProfile,
    current: PictureFormat,
    onChange: (PictureFormat) -> Unit
) {
    val choices = remember(profile) { TvOverridesStore.supportedFormats(profile) }
    val shown = current.takeIf { it in choices } ?: PictureFormat.BEST_FOR_TV
    FocusRow(onClick = {
        if (choices.size > 1) onChange(choices[(choices.indexOf(shown) + 1) % choices.size])
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.tv_check_preferred_format),
                fontSize = 19.sp,
                modifier = Modifier.weight(1f)
            )
            Text(text = formatLabel(shown), color = Dim, fontSize = 18.sp)
            if (choices.size > 1) Text(text = "  ›", color = Dim, fontSize = 20.sp)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.10f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
            pressedContainerColor = Color.White.copy(alpha = 0.85f),
            pressedContentColor = Color.Black
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
    ) {
        Text(
            text = text,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp)
        )
    }
}
