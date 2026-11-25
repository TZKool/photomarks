package com.tracemark.watermark.ui.editor

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tracemark.watermark.AppContainer
import com.tracemark.watermark.data.AppSettings
import com.tracemark.watermark.export.PhotoExporter
import com.tracemark.watermark.geo.GeoPoi
import com.tracemark.watermark.geo.GeoProviderRegistry
import com.tracemark.watermark.model.Anchor
import com.tracemark.watermark.model.FieldConfig
import com.tracemark.watermark.model.TemplateStyle
import com.tracemark.watermark.model.WatermarkTemplate
import com.tracemark.watermark.ui.components.AnchorPicker
import com.tracemark.watermark.ui.components.InfoTag
import com.tracemark.watermark.ui.components.SectionLabel
import com.tracemark.watermark.ui.components.TemplateThumbnail
import com.tracemark.watermark.ui.components.WatermarkPreview
import com.tracemark.watermark.ui.components.formatDistance
import com.tracemark.watermark.ui.theme.Danger
import com.tracemark.watermark.ui.theme.Gold
import com.tracemark.watermark.ui.theme.HairLine
import com.tracemark.watermark.ui.theme.Ink
import com.tracemark.watermark.ui.theme.Mint
import com.tracemark.watermark.ui.theme.Panel
import com.tracemark.watermark.ui.theme.PanelHigh
import com.tracemark.watermark.ui.theme.TextSecondary
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    uri: Uri,
    container: AppContainer,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onSaveAsTemplate: (WatermarkTemplate) -> Unit
) {
    val vm: EditorViewModel = viewModel(
        key = "editor_$uri",
        factory = EditorViewModel.factory(container, uri)
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val transform by vm.transform.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var showProviders by remember { mutableStateOf(false) }
    var showPois by remember { mutableStateOf(false) }
    var showAnchors by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    var compare by remember { mutableStateOf(false) }

    val providerSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val poiSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val successSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(state.exported) { showSuccess = state.exported != null }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        vm.clearMessage()
    }

    Scaffold(
        containerColor = Ink,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            EditorTopBar(
                title = state.template?.name ?: "编辑水印",
                onBack = onBack,
                onSettings = onOpenSettings
            )
            when {
                state.error != null -> {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error!!, color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(onClick = onBack) { Text("返回") }
                        }
                    }
                }
                state.loading || state.preview == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LinearProgressIndicator(Modifier.width(140.dp))
                            Spacer(Modifier.height(14.dp))
                            Text("正在读取照片与 EXIF…", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
                else -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color.Black)
                    ) {
                        WatermarkPreview(
                            bitmap = state.preview!!,
                            template = state.template,
                            text = state.formatted,
                            transform = transform,
                            showOriginal = compare,
                            onGesture = vm::onGesture,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                        if (state.providerName.isNotBlank() || state.addressLoading) {
                            Row(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (state.addressLoading) {
                                    InfoTag("解析地址中…", Gold)
                                } else {
                                    val label = buildString {
                                        append(state.providerName)
                                        state.precision?.let { append(" · ").append(it.label) }
                                    }
                                    InfoTag(label, Mint)
                                }
                            }
                        }
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp)
                                .background(Color(0x73000000), CircleShape)
                                .border(1.dp, Color(0x2EFFFFFF), CircleShape)
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        compare = true
                                        waitForUpOrCancellation()
                                        compare = false
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("按住看原图", fontSize = 11.sp, color = Color(0xFFD6DCE5))
                        }
                    }

                    ControlPanel(
                        state = state,
                        scale = transform.scale,
                        onTemplate = vm::selectTemplate,
                        onFields = vm::updateFields,
                        onScale = vm::setScale,
                        onReset = vm::resetTransform,
                        onAnchor = { showAnchors = true },
                        onAddress = vm::setAddress,
                        onNote = vm::setNote,
                        onRefresh = { vm.refreshAddress() },
                        onCandidates = { showPois = true },
                        onProviders = { showProviders = true },
                        onWeather = { vm.refreshWeather() },
                        onUseNow = { vm.useCurrentTime() },
                        onExport = vm::export,
                        onSaveTemplate = { state.template?.let(onSaveAsTemplate) }
                    )
                }
            }
        }
    }

    if (showProviders) {
        ModalBottomSheet(
            onDismissRequest = { showProviders = false },
            sheetState = providerSheet,
            containerColor = PanelHigh
        ) {
            ProviderSheet(
                settings = state.settings,
                onPick = { id ->
                    showProviders = false
                    vm.refreshAddress(id)
                },
                onConfigure = {
                    showProviders = false
                    onOpenSettings()
                }
            )
        }
    }

    if (showPois) {
        ModalBottomSheet(
            onDismissRequest = { showPois = false },
            sheetState = poiSheet,
            containerColor = PanelHigh
        ) {
            PoiSheet(
                candidates = state.candidates,
                onPick = {
                    vm.choosePoi(it)
                    showPois = false
                },
                onRetry = {
                    showPois = false
                    vm.refreshAddress()
                }
            )
        }
    }

    if (showAnchors) {
        AlertDialog(
            onDismissRequest = { showAnchors = false },
            title = { Text("水印位置") },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AnchorPicker(
                        selected = state.template?.style?.anchor ?: Anchor.BOTTOM_LEFT,
                        onSelect = { vm.setAnchor(it) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAnchors = false }) { Text("完成") }
            },
            containerColor = PanelHigh
        )
    }

    if (showSuccess) {
        ModalBottomSheet(
            onDismissRequest = {
                showSuccess = false
                vm.clearExportResult()
            },
            sheetState = successSheet,
            containerColor = PanelHigh
        ) {
            ExportSuccessSheet(
                fileName = state.exported?.fileName.orEmpty(),
                size = state.exported?.let { "${it.width} × ${it.height}" }.orEmpty(),
                onShare = {
                    state.exported?.let { result ->
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(PhotoExporter.shareIntent(result.uri), "分享水印照片")
                            )
                        }
                    }
                },
                onDone = {
                    showSuccess = false
                    vm.clearExportResult()
                    onBack()
                },
                onContinue = {
                    showSuccess = false
                    vm.clearExportResult()
                }
            )
        }
    }
}

@Composable
private fun EditorTopBar(title: String, onBack: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(
                "实时预览 · 拖动 / 双指缩放 / 旋转",
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Rounded.Tune, contentDescription = "设置", tint = Color.White)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ControlPanel(
    state: EditorUiState,
    scale: Float,
    onTemplate: (String) -> Unit,
    onFields: ((FieldConfig) -> FieldConfig) -> Unit,
    onScale: (Float) -> Unit,
    onReset: () -> Unit,
    onAnchor: () -> Unit,
    onAddress: (String) -> Unit,
    onNote: (String) -> Unit,
    onRefresh: () -> Unit,
    onCandidates: () -> Unit,
    onProviders: () -> Unit,
    onWeather: () -> Unit,
    onUseNow: () -> Unit,
    onExport: () -> Unit,
    onSaveTemplate: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        color = Panel,
        modifier = Modifier.fillMaxWidth().heightIn(max = 432.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp, bottom = 2.dp)
                    .size(38.dp, 4.dp)
                    .background(HairLine, CircleShape)
            )
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
            ) {
                AddressBlock(
                    state = state,
                    onAddress = onAddress,
                    onRefresh = onRefresh,
                    onCandidates = onCandidates,
                    onProviders = onProviders,
                    onWeather = onWeather,
                    onUseNow = onUseNow
                )

                SectionLabel("水印模板") {
                    Text("${state.templates.size} 套", fontSize = 11.sp, color = TextSecondary)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.templates, key = { it.id }) { template ->
                        TemplateCard(
                            template = template,
                            selected = template.id == state.template?.id,
                            onClick = { onTemplate(template.id) }
                        )
                    }
                }

                SectionLabel("显示内容")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val fieldChips = listOf<Triple<String, Boolean, () -> Unit>>(
                        Triple("地址", state.template?.fields?.showAddress == true) {
                            onFields { it.copy(showAddress = !it.showAddress) }
                        },
                        Triple("经纬度", state.template?.fields?.showCoords == true) {
                            onFields { it.copy(showCoords = !it.showCoords) }
                        },
                        Triple("时间", state.template?.fields?.showTime == true) {
                            onFields { it.copy(showTime = !it.showTime) }
                        },
                        Triple("日期", state.template?.fields?.showDate == true) {
                            onFields { it.copy(showDate = !it.showDate) }
                        },
                        Triple("星期", state.template?.fields?.showWeek == true) {
                            onFields { it.copy(showWeek = !it.showWeek) }
                        },
                        Triple("天气", state.template?.fields?.showWeather == true) {
                            onFields { it.copy(showWeather = !it.showWeather) }
                        },
                        Triple("海拔", state.template?.fields?.showAltitude == true) {
                            onFields { it.copy(showAltitude = !it.showAltitude) }
                        },
                        Triple("备注", state.template?.fields?.showNote == true) {
                            onFields { it.copy(showNote = !it.showNote) }
                        },
                        Triple("设备", state.template?.fields?.showDevice == true) {
                            onFields { it.copy(showDevice = !it.showDevice) }
                        },
                        Triple("品牌标识", state.template?.fields?.showLogo == true) {
                            onFields { it.copy(showLogo = !it.showLogo) }
                        },
                        Triple("防伪码", state.template?.fields?.showCode == true) {
                            onFields { it.copy(showCode = !it.showCode) }
                        },
                        Triple("读秒", state.template?.fields?.timeWithSeconds == true) {
                            onFields { it.copy(timeWithSeconds = !it.timeWithSeconds) }
                        }
                    )
                    fieldChips.forEach { (label, selected, toggle) ->
                        FilterChip(
                            selected = selected,
                            onClick = toggle,
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Gold.copy(alpha = 0.2f),
                                selectedLabelColor = Gold,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }

                SectionLabel("排版与位置")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onAnchor, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
                        Icon(Icons.Rounded.Place, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("位置", fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = onReset, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("复位", fontSize = 12.sp)
                    }
                    Text("大小", fontSize = 12.sp, color = TextSecondary)
                    Slider(
                        value = scale,
                        onValueChange = onScale,
                        valueRange = 0.4f..2.5f,
                        modifier = Modifier.weight(1f)
                    )
                }

                SectionLabel("备注")
                OutlinedTextField(
                    value = state.source.note,
                    onValueChange = onNote,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("写点现场说明，例如「3 号楼封顶验收」", fontSize = 13.sp) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onSaveTemplate, enabled = state.template != null) {
                        Icon(Icons.Rounded.Save, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("另存为模板", fontSize = 12.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "拖拽水印可微调位置",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            ExportBar(state = state, onExport = onExport)
        }
    }
}

@Composable
private fun AddressBlock(
    state: EditorUiState,
    onAddress: (String) -> Unit,
    onRefresh: () -> Unit,
    onCandidates: () -> Unit,
    onProviders: () -> Unit,
    onWeather: () -> Unit,
    onUseNow: () -> Unit
) {
    Column(Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("地址与坐标", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.width(8.dp))
            if (state.hasGps) {
                InfoTag(
                    "${state.source.latitude!!.format(6)}, ${state.source.longitude!!.format(6)}",
                    Gold
                )
            } else {
                InfoTag("无 GPS", Danger)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (!state.hasGps) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.LocationOff, null, Modifier.size(16.dp), tint = TextSecondary)
                Spacer(Modifier.width(6.dp))
                Text(
                    "这张照片没有写入 GPS 信息，可以手动填写地址，或检查相机是否开启定位。",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(
            value = state.source.address,
            onValueChange = onAddress,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("完整地址", fontSize = 13.sp, color = TextSecondary) },
            minLines = 2,
            maxLines = 4,
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Gold,
                unfocusedBorderColor = HairLine,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )
        if (state.addressLoading) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Gold)
        }
        state.addressError?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, fontSize = 11.5.sp, color = Danger)
        }
        if (state.candidates.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "已找到 ${state.candidates.size} 个附近地点，可切换以提升精度",
                fontSize = 11.5.sp,
                color = Mint
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.hasGps) {
                TextButton(onClick = onRefresh) { Icon(Icons.Rounded.MyLocation, null, Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("重新解析", fontSize = 12.sp) }
                TextButton(onClick = onCandidates, enabled = state.candidates.isNotEmpty()) { Icon(Icons.Rounded.Place, null, Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("附近地点", fontSize = 12.sp) }
                TextButton(onClick = onProviders) { Icon(Icons.Rounded.Public, null, Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("解析服务", fontSize = 12.sp) }
                TextButton(onClick = onWeather) { Text("取天气", fontSize = 12.sp) }
            } else {
                TextButton(onClick = onUseNow) { Text("使用当前时间", fontSize = 12.sp) }
                TextButton(onClick = onProviders) { Text("解析服务", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun ExportBar(state: EditorUiState, onExport: () -> Unit) {
    Surface(color = Panel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
            if (state.exporting) {
                LinearProgressIndicator(
                    progress = { state.exportProgress },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = Gold,
                    trackColor = HairLine
                )
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = onExport,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = !state.exporting && state.template != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Gold,
                    contentColor = Color(0xFF201603)
                )
            ) {
                Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.exporting) "正在导出 ${(state.exportProgress * 100).roundToInt()}%"
                    else "导出高清照片",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun TemplateCard(template: WatermarkTemplate, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(122.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(84.dp)
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) Gold else HairLine,
                    RoundedCornerShape(14.dp)
                )
                .padding(if (selected) 2.dp else 1.dp)
        ) {
            TemplateThumbnail(template, Modifier.fillMaxSize(), corner = 11.dp)
        }
        Spacer(Modifier.height(5.dp))
        Text(
            template.name,
            fontSize = 11.5.sp,
            color = if (selected) Gold else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp)
        )
    }
}

@Composable
private fun ProviderSheet(
    settings: AppSettings,
    onPick: (String) -> Unit,
    onConfigure: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 30.dp)
    ) {
        Text("逆地理解析服务", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(
            "已配置 Key 的服务会优先使用；精度越高，水印地址越具体。",
            fontSize = 12.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(14.dp))
        ProviderRow(
            title = "智能优选",
            subtitle = "自动组合百度 / 高德 / 腾讯等，取精度最高结果",
            selected = settings.geoProvider == "auto",
            onClick = { onPick("auto") }
        )
        GeoProviderRegistry.all.forEach { provider ->
            val info = provider.info
            val configured = !info.requiresKey || settings.keyFor(info.id).isNotBlank()
            ProviderRow(
                title = info.name,
                subtitle = if (configured) info.note else "需要 ${info.keyLabel}，配置后可启用",
                selected = settings.geoProvider == info.id,
                enabled = configured,
                action = if (configured) null else "去配置",
                onClick = { if (configured) onPick(info.id) else onConfigure() }
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "提示：带 Key 的国内服务（高德 / 百度 / 腾讯 / 天地图）能返回门牌号与 AOI/POI，精度通常高于免费服务。",
            fontSize = 11.sp,
            color = TextSecondary
        )
    }
}

@Composable
private fun ProviderRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean = true,
    action: String? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Column(Modifier.weight(1f).padding(start = 2.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, fontSize = 11.5.sp, color = TextSecondary)
        }
        if (action != null) {
            Text(action, fontSize = 12.sp, color = Gold, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun PoiSheet(candidates: List<GeoPoi>, onPick: (GeoPoi) -> Unit, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 26.dp)
    ) {
        Text("附近地点", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(
            "选择离拍摄点最近的地点，水印地址会更精确。",
            fontSize = 12.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(10.dp))
        candidates.forEach { poi ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(poi) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Place, null, Modifier.size(18.dp), tint = Gold)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(poi.name, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                    val sub = listOfNotNull(
                        poi.category?.takeIf { it.isNotBlank() },
                        poi.address?.takeIf { it.isNotBlank() },
                        formatDistance(poi.distanceMeters).takeIf { it.isNotBlank() }
                    ).joinToString(" · ")
                    if (sub.isNotBlank()) Text(sub, fontSize = 11.5.sp, color = TextSecondary)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onRetry) { Text("重新解析地址", fontSize = 12.sp) }
    }
}

@Composable
private fun ExportSuccessSheet(
    fileName: String,
    size: String,
    onShare: () -> Unit,
    onDone: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(52.dp), tint = Mint)
        Spacer(Modifier.height(10.dp))
        Text("导出完成", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text("已保存到相册 Pictures/TraceMark", fontSize = 12.sp, color = TextSecondary)
        Spacer(Modifier.height(10.dp))
        Surface(color = PanelHigh, shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(fileName, fontSize = 12.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (size.isNotBlank()) Text(size, fontSize = 11.sp, color = TextSecondary)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onContinue) { Text("继续编辑") }
            Button(
                onClick = onShare,
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF201603))
            ) {
                Icon(Icons.Rounded.Share, null, Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("分享")
            }
            TextButton(onClick = onDone) { Text("完成") }
        }
    }
}

private fun Double.format(decimals: Int): String = String.format(java.util.Locale.US, "%.${decimals}f", this)
