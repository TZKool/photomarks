package com.tracemark.watermark.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tracemark.watermark.AppContainer
import com.tracemark.watermark.BuildConfig
import com.tracemark.watermark.geo.GeoProviderRegistry
import com.tracemark.watermark.model.AddressLevel
import com.tracemark.watermark.model.CoordinateSystem
import com.tracemark.watermark.ui.components.GlassCard
import com.tracemark.watermark.ui.components.SectionLabel
import com.tracemark.watermark.ui.components.TemplateThumbnail
import com.tracemark.watermark.ui.theme.Gold
import com.tracemark.watermark.ui.theme.HairLine
import com.tracemark.watermark.ui.theme.Ink
import com.tracemark.watermark.ui.theme.Mint
import com.tracemark.watermark.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val settings by vm.settings.collectAsStateWithLifecycle()
    val templates by vm.templates.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var keysExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = Ink,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
                Text("设置", style = MaterialTheme.typography.titleLarge, color = Color.White)
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 36.dp)
            ) {
                SectionLabel("逆地理解析")
                GlassCard {
                    Column {
                        Text("解析服务", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(
                            "自动模式会按精度依次尝试已配置的服务，命中门牌 / POI 即停止。",
                            fontSize = 11.5.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                        )
                        ProviderChoice(
                            title = "智能优选（推荐）",
                            subtitle = "自动组合多个服务",
                            selected = settings.geoProvider == "auto",
                            onClick = { vm.update { it.copy(geoProvider = "auto") } }
                        )
                        GeoProviderRegistry.all.forEach { provider ->
                            val info = provider.info
                            val configured = !info.requiresKey || settings.keyFor(info.id).isNotBlank()
                            ProviderChoice(
                                title = info.name,
                                subtitle = if (!info.requiresKey) "免费 · 无需 Key" else if (configured) "已配置 Key" else "需要 ${info.keyLabel}",
                                selected = settings.geoProvider == info.id,
                                onClick = {
                                    if (!configured) {
                                        keysExpanded = true
                                        scope.launch { snackbar.showSnackbar("请先填写 ${info.name} 的 Key") }
                                    } else {
                                        vm.update { it.copy(geoProvider = info.id) }
                                    }
                                }
                            )
                        }

                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = HairLine)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "API Key 配置",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${settings.keysConfigured()} / 6", fontSize = 12.sp, color = Gold)
                            Icon(
                                Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(if (keysExpanded) 180f else 0f)
                            )
                        }
                        Text(
                            "Key 只保存在本机，用于请求对应地图服务。",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        if (!keysExpanded) {
                            TextButton(onClick = { keysExpanded = true }) { Text("展开填写", fontSize = 12.sp) }
                        }
                        AnimatedVisibility(visible = keysExpanded) {
                            Column {
                                Spacer(Modifier.height(4.dp))
                                listOf("amap", "baidu", "tencent", "tianditu", "mapbox", "geoapify")
                                    .forEach { id ->
                                        val info = GeoProviderRegistry.info(id) ?: return@forEach
                                        KeyField(
                                            label = info.name,
                                            hint = info.keyHint,
                                            value = settings.keyFor(id),
                                            onValue = { value -> vm.update { it.withKey(id, value.trim()) } },
                                            keyUrl = info.keyUrl
                                        )
                                    }
                            }
                        }

                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = HairLine)
                        Text("地址精度", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(
                            when (settings.addressLevel) {
                                AddressLevel.POI -> "优先返回兴趣点名称，如「天安门广场东侧路」旁的具体地点。"
                                AddressLevel.DOOR -> "优先返回门牌号级地址，适合工程验收与取证。"
                                AddressLevel.STREET -> "只要求到道路级，响应更快、更稳定。"
                            },
                            fontSize = 11.5.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AddressLevel.values().forEach { level ->
                                val label = when (level) {
                                    AddressLevel.STREET -> "道路级"
                                    AddressLevel.DOOR -> "门牌号"
                                    AddressLevel.POI -> "POI 兴趣点"
                                }
                                FilterChip(
                                    selected = settings.addressLevel == level,
                                    onClick = { vm.update { it.copy(addressLevel = level) } },
                                    label = { Text(label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Gold.copy(alpha = 0.2f),
                                        selectedLabelColor = Gold,
                                        labelColor = TextSecondary
                                    )
                                )
                            }
                        }
                        TextButton(
                            onClick = {
                                vm.clearGeoCache()
                                scope.launch { snackbar.showSnackbar("已清空地址缓存") }
                            }
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(16.dp), tint = TextSecondary)
                            Spacer(Modifier.width(4.dp))
                            Text("清空地址缓存", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                }

                SectionLabel("坐标与时间")
                GlassCard {
                    Column {
                        Text("坐标基准", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(
                            "水印显示与导出使用的坐标系。与地图平台一致可避免位置偏移。",
                            fontSize = 11.5.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CoordinateSystem.values().forEach { system ->
                                val label = when (system) {
                                    CoordinateSystem.WGS84 -> "WGS84 · 原始 GPS"
                                    CoordinateSystem.GCJ02 -> "GCJ-02 · 高德腾讯"
                                    CoordinateSystem.BD09 -> "BD-09 · 百度"
                                }
                                FilterChip(
                                    selected = settings.coordSystem == system,
                                    onClick = { vm.update { it.copy(coordSystem = system) } },
                                    label = { Text(label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Gold.copy(alpha = 0.2f),
                                        selectedLabelColor = Gold,
                                        labelColor = TextSecondary
                                    )
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        SwitchRow(
                            title = "自动获取拍摄天气",
                            subtitle = "根据 GPS 与拍摄日期调用 Open-Meteo，无需 Key",
                            checked = settings.autoWeather,
                            onChecked = { value -> vm.update { it.copy(autoWeather = value) } }
                        )
                    }
                }

                SectionLabel("导出")
                GlassCard {
                    Column {
                        Text("最大边长", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(
                            "导出会保持原图比例，水印按比例同步缩放。",
                            fontSize = 11.5.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(4096 to "4096px", 6000 to "6000px", 8000 to "8000px", 12000 to "12000px")
                                .forEach { (size, label) ->
                                    FilterChip(
                                        selected = settings.exportMaxSide == size,
                                        onClick = { vm.update { it.copy(exportMaxSide = size) } },
                                        label = { Text(label, fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Gold.copy(alpha = 0.2f),
                                            selectedLabelColor = Gold,
                                            labelColor = TextSecondary
                                        )
                                    )
                                }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("JPEG 质量", fontSize = 13.sp, color = Color.White)
                            Slider(
                                value = settings.jpegQuality.toFloat(),
                                onValueChange = { value ->
                                    vm.update { it.copy(jpegQuality = value.roundToInt()) }
                                },
                                valueRange = 70f..100f,
                                modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                            )
                            Text("${settings.jpegQuality}", fontSize = 12.sp, color = Gold)
                        }
                        SwitchRow(
                            title = "保留 EXIF 信息",
                            subtitle = "导出时写回相机的拍摄参数与 GPS",
                            checked = settings.keepExif,
                            onChecked = { value -> vm.update { it.copy(keepExif = value) } }
                        )

                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = HairLine)
                        Text("默认模板", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(templates, key = { it.id }) { template ->
                                val selected = settings.defaultTemplateId == template.id
                                Column(
                                    Modifier
                                        .width(96.dp)
                                        .clickable { vm.update { it.copy(defaultTemplateId = template.id) } }
                                ) {
                                    TemplateThumbnail(
                                        template = template,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(64.dp)
                                            .border(
                                                if (selected) 2.dp else 1.dp,
                                                if (selected) Gold else HairLine,
                                                RoundedCornerShape(12.dp)
                                            )
                                            .padding(if (selected) 2.dp else 1.dp),
                                        corner = if (selected) 10.dp else 9.dp
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        template.name,
                                        fontSize = 11.sp,
                                        color = if (selected) Gold else TextSecondary,
                                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                SectionLabel("外观")
                GlassCard {
                    SwitchRow(
                        title = "深色主题",
                        subtitle = "编辑照片时更专注，也更省电",
                        checked = settings.darkTheme,
                        onChecked = { value -> vm.update { it.copy(darkTheme = value) } }
                    )
                }

                SectionLabel("关于")
                GlassCard {
                    Column {
                        Text(
                            "迹印相机 v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "读取照片 EXIF 中的拍摄时间、设备与 GPS，将经纬度与逆解析地址绘制为可拖拽的水印。\n\n" +
                                "内置 6 套参考元道经纬相机与常见设计稿的模板，可在「模板工作室」中自由改色、改版式、增删字段。\n\n" +
                                "逆地理解析支持高德、百度、腾讯、天地图、Mapbox、Geoapify、Photon、Nominatim、BigDataCloud，国内服务建议配置 Key 以获得门牌号级精度。",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun ProviderChoice(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 14.sp,
                color = if (enabled) Color.White else TextSecondary
            )
            Text(subtitle, fontSize = 11.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun KeyField(
    label: String,
    hint: String,
    value: String,
    onValue: (String) -> Unit,
    keyUrl: String
) {
    val context = LocalContext.current
    Column(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.5.sp, color = Color.White, modifier = Modifier.weight(1f))
            if (keyUrl.isNotBlank()) {
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(keyUrl)))
                        }
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
                ) {
                    Text("获取 Key", fontSize = 11.sp, color = Gold)
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(12.dp), tint = Gold)
                }
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(hint, fontSize = 11.5.sp, color = TextSecondary) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            shape = RoundedCornerShape(10.dp)
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = Color.White)
            Text(subtitle, fontSize = 11.sp, color = TextSecondary)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
