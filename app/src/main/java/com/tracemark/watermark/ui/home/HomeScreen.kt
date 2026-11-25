package com.tracemark.watermark.ui.home

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tracemark.watermark.export.PhotoExporter
import com.tracemark.watermark.ui.components.GlassCard
import com.tracemark.watermark.ui.components.RecentShotThumb
import com.tracemark.watermark.ui.components.SectionLabel
import com.tracemark.watermark.ui.components.TemplateThumbnail
import com.tracemark.watermark.ui.theme.Gold
import com.tracemark.watermark.ui.theme.HairLine
import com.tracemark.watermark.ui.theme.Ink
import com.tracemark.watermark.ui.theme.Mint
import com.tracemark.watermark.ui.theme.Panel
import com.tracemark.watermark.ui.theme.TextPrimary
import com.tracemark.watermark.ui.theme.TextSecondary
import com.tracemark.watermark.model.WatermarkTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    onPickPhoto: () -> Unit,
    onOpenStudio: (WatermarkTemplate?) -> Unit,
    onOpenSettings: () -> Unit,
    templates: List<WatermarkTemplate>
) {
    var recent by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        recent = withContext(Dispatchers.IO) { queryRecentExports(context) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF10141B), Ink, Color(0xFF080A0E))
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(38.dp)
                        .background(Gold, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Place, null, Modifier.size(22.dp), tint = Color(0xFF201603))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("迹印相机", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text("真实经纬 · 可验水印", fontSize = 11.sp, color = TextSecondary)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "设置", tint = TextSecondary)
                }
            }

            Spacer(Modifier.height(18.dp))
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Transparent
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF32280F), Color(0xFF171B23), Color(0xFF12161E))
                            )
                        )
                        .border(1.dp, Gold.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                        .padding(20.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(14.dp), tint = Gold)
                            Spacer(Modifier.width(5.dp))
                            Text("让每张照片都有据可查", fontSize = 11.sp, color = Gold, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "给照片盖上\n真实可验的经纬戳",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            lineHeight = 34.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "自动读取 EXIF 拍摄时间与 GPS，逆解析成精确地址，实时预览后再以原图画质导出。",
                            fontSize = 12.5.sp,
                            color = TextSecondary,
                            lineHeight = 19.sp
                        )
                        Spacer(Modifier.height(18.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = onPickPhoto,
                                modifier = Modifier.height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Gold,
                                    contentColor = Color(0xFF201603)
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("选择照片", fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.width(6.dp))
                            TextButton(onClick = { onOpenStudio(null) }) {
                                Icon(Icons.Rounded.Tune, null, Modifier.size(15.dp), tint = Gold)
                                Spacer(Modifier.width(4.dp))
                                Text("模板工作室", fontSize = 12.5.sp, color = Gold)
                            }
                        }
                    }
                }
            }

            SectionLabel("内置模板") {
                TextButton(onClick = { onOpenStudio(null) }) {
                    Text("全部 ${templates.size} 套", fontSize = 11.5.sp, color = Gold)
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(templates, key = { it.id }) { template ->
                    Column(
                        Modifier
                            .width(158.dp)
                            .clickable { onOpenStudio(template) }
                    ) {
                        TemplateThumbnail(
                            template = template,
                            modifier = Modifier.fillMaxWidth().height(112.dp),
                            corner = 16.dp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            template.name,
                            fontSize = 13.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            template.description,
                            fontSize = 10.5.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            SectionLabel("能力")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureCard(
                    icon = Icons.Rounded.CameraAlt,
                    title = "EXIF 自动识别",
                    subtitle = "时间 / 设备 / GPS",
                    modifier = Modifier.weight(1f)
                )
                FeatureCard(
                    icon = Icons.Rounded.Public,
                    title = "9 家逆解析",
                    subtitle = "门牌 / POI 精度",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureCard(
                    icon = Icons.Rounded.Gesture,
                    title = "拖拽实时预览",
                    subtitle = "缩放 / 旋转 / 换位",
                    modifier = Modifier.weight(1f)
                )
                FeatureCard(
                    icon = Icons.Rounded.Download,
                    title = "原图画质导出",
                    subtitle = "保留 EXIF 参数",
                    modifier = Modifier.weight(1f)
                )
            }

            if (recent.isNotEmpty()) {
                SectionLabel("最近导出")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recent, key = { it.toString() }) { uri ->
                        RecentShotThumb(
                            uri = uri.toString(),
                            modifier = Modifier
                                .size(86.dp)
                                .clickable {
                                    runCatching {
                                        context.startActivity(
                                            Intent.createChooser(
                                                PhotoExporter.shareIntent(uri),
                                                "分享水印照片"
                                            )
                                        )
                                    }
                                }
                        )
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .background(Mint.copy(alpha = 0.16f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Place, null, Modifier.size(14.dp), tint = Mint)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("精度建议", fontSize = 12.5.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "在国内使用时，建议在设置中填入高德 / 百度 / 腾讯的 Key，可返回门牌号与 AOI 级地址；免费服务（Photon、Nominatim、BigDataCloud）无需配置但精度有限。",
                        fontSize = 11.5.sp,
                        color = TextSecondary,
                        lineHeight = 17.sp
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Panel.copy(alpha = 0.85f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.border(1.dp, HairLine, RoundedCornerShape(16.dp)).padding(14.dp)) {
            Icon(icon, null, Modifier.size(20.dp), tint = Gold)
            Spacer(Modifier.height(10.dp))
            Text(title, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 10.5.sp, color = TextSecondary)
        }
    }
}

private fun queryRecentExports(context: Context): List<Uri> {
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.RELATIVE_PATH
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.DATA
    }
    return runCatching {
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            "$pathColumn LIKE ?",
            arrayOf("%Pictures/TraceMark%"),
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            buildList {
                while (cursor.moveToNext() && size < 12) {
                    add(ContentUris.withAppendedId(collection, cursor.getLong(idColumn)))
                }
            }
        } ?: emptyList()
    }.getOrDefault(emptyList())
}
