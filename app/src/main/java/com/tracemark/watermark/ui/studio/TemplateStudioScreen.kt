package com.tracemark.watermark.ui.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tracemark.watermark.AppContainer
import com.tracemark.watermark.model.Anchor
import com.tracemark.watermark.model.BackdropStyle
import com.tracemark.watermark.model.BuiltInTemplates
import com.tracemark.watermark.model.FieldConfig
import com.tracemark.watermark.model.TemplateLayout
import com.tracemark.watermark.model.WatermarkTemplate
import com.tracemark.watermark.ui.components.AnchorPicker
import com.tracemark.watermark.ui.components.GlassCard
import com.tracemark.watermark.ui.components.SectionLabel
import com.tracemark.watermark.ui.components.TemplateThumbnail
import com.tracemark.watermark.ui.theme.AccentPreset
import com.tracemark.watermark.ui.theme.Gold
import com.tracemark.watermark.ui.theme.HairLine
import com.tracemark.watermark.ui.theme.Ink
import com.tracemark.watermark.ui.theme.Panel
import com.tracemark.watermark.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TemplateStudioScreen(
    container: AppContainer,
    initial: WatermarkTemplate?,
    onBack: () -> Unit
) {
    val base = initial ?: BuiltInTemplates.defaultFor(null)
    val isUserTemplate = !base.builtIn && base.id.isNotBlank()
    var template by remember(initial) {
        mutableStateOf(
            if (isUserTemplate) base
            else base.copy(id = "", builtIn = false, name = "${base.name} 自定义")
        )
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDelete by remember { mutableStateOf(false) }

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
                Column(Modifier.weight(1f)) {
                    Text("模板工作室", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(
                        if (isUserTemplate) "编辑自定义模板" else "基于内置模板创建新模板",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                if (isUserTemplate) {
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "删除", tint = TextSecondary)
                    }
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(6.dp))
                TemplateThumbnail(
                    template = template,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(212.dp)
                        .border(1.dp, HairLine, MaterialTheme.shapes.large),
                    corner = 18.dp
                )

                SectionLabel("模板信息")
                GlassCard {
                    Column {
                        OutlinedTextField(
                            value = template.name,
                            onValueChange = { template = template.copy(name = it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("模板名称", fontSize = 12.sp) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = template.description,
                            onValueChange = { template = template.copy(description = it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("简介", fontSize = 12.sp) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = template.style.brandLabel,
                            onValueChange = { value ->
                                template = template.copy(style = template.style.copy(brandLabel = value))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("品牌文字", fontSize = 12.sp) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                SectionLabel("版式")
                GlassCard {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TemplateLayout.values().forEach { layout ->
                            FilterChip(
                                selected = template.style.layout == layout,
                                onClick = {
                                    template = template.copy(style = template.style.copy(layout = layout))
                                },
                                label = { Text(layoutLabel(layout), fontSize = 12.sp) },
                                colors = chipColors()
                            )
                        }
                    }
                }

                SectionLabel("主色")
                GlassCard {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AccentPreset.values().forEach { preset ->
                            val selected = template.style.accentColor == preset.argb.toInt()
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier
                                        .size(38.dp)
                                        .background(preset.color, CircleShape)
                                        .border(
                                            if (selected) 2.dp else 1.dp,
                                            if (selected) Color.White else HairLine,
                                            CircleShape
                                        )
                                        .clickable {
                                            template = template.copy(
                                                style = template.style.copy(accentColor = preset.argb.toInt())
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            null,
                                            Modifier.size(18.dp),
                                            tint = if (preset == AccentPreset.GRAPHITE) Color.Black else Color.White
                                        )
                                    }
                                }
                                Text(preset.label, fontSize = 10.sp, color = TextSecondary)
                            }
                        }
                    }
                }

                SectionLabel("底色与位置")
                GlassCard {
                    Column {
                        Text("水印底色", fontSize = 12.5.sp, color = Color.White)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                BackdropStyle.NONE to "无底色",
                                BackdropStyle.CARD to "白色卡片",
                                BackdropStyle.SCRIM to "渐变暗角"
                            ).forEach { (style, label) ->
                                FilterChip(
                                    selected = template.style.background == style,
                                    onClick = {
                                        template = template.copy(style = template.style.copy(background = style))
                                    },
                                    label = { Text(label, fontSize = 12.sp) },
                                    colors = chipColors()
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("锚点位置", fontSize = 12.5.sp, color = Color.White, modifier = Modifier.weight(1f))
                            AnchorPicker(
                                selected = template.style.anchor,
                                onSelect = { anchor ->
                                    template = template.copy(style = template.style.copy(anchor = anchor))
                                }
                            )
                        }
                    }
                }

                SectionLabel("尺寸与质感")
                GlassCard {
                    Column {
                        SliderRow(
                            label = "字号",
                            value = template.style.fontScale,
                            range = 0.75f..1.6f,
                            display = String.format(Locale.US, "%.2f×", template.style.fontScale),
                            onChange = { value ->
                                template = template.copy(style = template.style.copy(fontScale = value))
                            }
                        )
                        SliderRow(
                            label = "边距",
                            value = template.style.marginRatio,
                            range = 0.02f..0.10f,
                            display = String.format(Locale.US, "%.1f%%", template.style.marginRatio * 100),
                            onChange = { value ->
                                template = template.copy(style = template.style.copy(marginRatio = value))
                            }
                        )
                        SliderRow(
                            label = "不透明度",
                            value = template.style.opacity,
                            range = 0.4f..1f,
                            display = String.format(Locale.US, "%.0f%%", template.style.opacity * 100),
                            onChange = { value ->
                                template = template.copy(style = template.style.copy(opacity = value))
                            }
                        )
                        SwitchRow(
                            title = "文字投影",
                            checked = template.style.shadow,
                            onChecked = { value ->
                                template = template.copy(style = template.style.copy(shadow = value))
                            }
                        )
                        SwitchRow(
                            title = "24 小时制",
                            checked = template.fields.time24h,
                            onChecked = { value ->
                                template = template.copy(fields = template.fields.copy(time24h = value))
                            }
                        )
                    }
                }

                SectionLabel("显示字段")
                GlassCard {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FieldChip("标题", template.fields.showHeader) {
                            template = template.copy(fields = template.fields.copy(showHeader = !template.fields.showHeader))
                        }
                        FieldChip("地址", template.fields.showAddress) {
                            template = template.copy(fields = template.fields.copy(showAddress = !template.fields.showAddress))
                        }
                        FieldChip("维度", template.fields.showLatitude) {
                            template = template.copy(fields = template.fields.copy(showLatitude = !template.fields.showLatitude))
                        }
                        FieldChip("经度", template.fields.showLongitude) {
                            template = template.copy(fields = template.fields.copy(showLongitude = !template.fields.showLongitude))
                        }
                        FieldChip("经纬度组", template.fields.showCoords) {
                            template = template.copy(fields = template.fields.copy(showCoords = !template.fields.showCoords))
                        }
                        FieldChip("时间", template.fields.showTime) {
                            template = template.copy(fields = template.fields.copy(showTime = !template.fields.showTime))
                        }
                        FieldChip("日期", template.fields.showDate) {
                            template = template.copy(fields = template.fields.copy(showDate = !template.fields.showDate))
                        }
                        FieldChip("星期", template.fields.showWeek) {
                            template = template.copy(fields = template.fields.copy(showWeek = !template.fields.showWeek))
                        }
                        FieldChip("天气", template.fields.showWeather) {
                            template = template.copy(fields = template.fields.copy(showWeather = !template.fields.showWeather))
                        }
                        FieldChip("海拔", template.fields.showAltitude) {
                            template = template.copy(fields = template.fields.copy(showAltitude = !template.fields.showAltitude))
                        }
                        FieldChip("精度", template.fields.showAccuracy) {
                            template = template.copy(fields = template.fields.copy(showAccuracy = !template.fields.showAccuracy))
                        }
                        FieldChip("设备", template.fields.showDevice) {
                            template = template.copy(fields = template.fields.copy(showDevice = !template.fields.showDevice))
                        }
                        FieldChip("备注", template.fields.showNote) {
                            template = template.copy(fields = template.fields.copy(showNote = !template.fields.showNote))
                        }
                        FieldChip("品牌标识", template.fields.showLogo) {
                            template = template.copy(fields = template.fields.copy(showLogo = !template.fields.showLogo))
                        }
                        FieldChip("防伪码", template.fields.showCode) {
                            template = template.copy(fields = template.fields.copy(showCode = !template.fields.showCode))
                        }
                        FieldChip("印章", template.fields.showSeal) {
                            template = template.copy(fields = template.fields.copy(showSeal = !template.fields.showSeal))
                        }
                        FieldChip("读秒", template.fields.timeWithSeconds) {
                            template = template.copy(fields = template.fields.copy(timeWithSeconds = !template.fields.timeWithSeconds))
                        }
                    }
                }

                SectionLabel("坐标小数位")
                GlassCard {
                    SliderRow(
                        label = "小数位",
                        value = template.fields.coordDecimals.toFloat(),
                        range = 3f..8f,
                        display = "${template.fields.coordDecimals} 位",
                        onChange = { value ->
                            template = template.copy(
                                fields = template.fields.copy(coordDecimals = value.toInt().coerceIn(3, 8))
                            )
                        }
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Panel)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isUserTemplate) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                container.templates.duplicate(template, "${template.name} 副本")
                                snackbar.showSnackbar("已另存副本")
                            }
                        }
                    ) { Text("另存副本", fontSize = 12.sp) }
                }
                Button(
                    onClick = {
                        val name = template.name.ifBlank { "未命名模板" }
                        scope.launch {
                            container.templates.save(template.copy(name = name))
                            snackbar.showSnackbar("模板已保存")
                            onBack()
                        }
                    },
                    modifier = Modifier.weight(1f).height(46.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold,
                        contentColor = Color(0xFF201603)
                    )
                ) {
                    Icon(Icons.Rounded.Save, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isUserTemplate) "保存修改" else "保存为新模板", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除模板") },
            text = { Text("「${base.name}」删除后不可恢复，已导出的照片不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    scope.launch {
                        container.templates.delete(base.id)
                        snackbar.showSnackbar("模板已删除")
                        onBack()
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun FieldChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        colors = chipColors()
    )
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Gold.copy(alpha = 0.2f),
    selectedLabelColor = Gold,
    labelColor = TextSecondary
)

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, fontSize = 12.5.sp, color = Color.White, modifier = Modifier.width(58.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f)
        )
        Text(display, fontSize = 11.5.sp, color = Gold, modifier = Modifier.width(52.dp))
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(title, fontSize = 12.5.sp, color = Color.White, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

private fun layoutLabel(layout: TemplateLayout): String = when (layout) {
    TemplateLayout.COORDINATE_LIST -> "经纬列表"
    TemplateLayout.HERO_TIME -> "极简大字"
    TemplateLayout.BRAND_BADGE -> "品牌徽标"
    TemplateLayout.CHECKIN_CARD -> "打卡卡片"
    TemplateLayout.GOLD_DIVIDER -> "金色分隔"
    TemplateLayout.BOTTOM_PILL -> "底部胶囊"
}
