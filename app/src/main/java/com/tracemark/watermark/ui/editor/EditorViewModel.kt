package com.tracemark.watermark.ui.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tracemark.watermark.AppContainer
import com.tracemark.watermark.data.AppSettings
import com.tracemark.watermark.data.PhotoMeta
import com.tracemark.watermark.export.ExportRequest
import com.tracemark.watermark.export.ExportResult
import com.tracemark.watermark.geo.GeoPoi
import com.tracemark.watermark.geo.GeoPrecision
import com.tracemark.watermark.model.Anchor
import com.tracemark.watermark.model.FieldConfig
import com.tracemark.watermark.model.FormattedWatermark
import com.tracemark.watermark.model.TemplateStyle
import com.tracemark.watermark.model.WatermarkSource
import com.tracemark.watermark.model.WatermarkTemplate
import com.tracemark.watermark.model.WatermarkTransform
import com.tracemark.watermark.util.WatermarkFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import android.graphics.Bitmap

data class EditorUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val preview: Bitmap? = null,
    val meta: PhotoMeta? = null,
    val source: WatermarkSource = WatermarkSource(),
    val addressLoading: Boolean = false,
    val addressError: String? = null,
    val candidates: List<GeoPoi> = emptyList(),
    val providerName: String = "",
    val precision: GeoPrecision? = null,
    val templates: List<WatermarkTemplate> = emptyList(),
    val template: WatermarkTemplate? = null,
    val formatted: FormattedWatermark? = null,
    val settings: AppSettings = AppSettings(),
    val exporting: Boolean = false,
    val exportProgress: Float = 0f,
    val exported: ExportResult? = null,
    val message: String? = null
) {
    val hasGps: Boolean get() = source.latitude != null && source.longitude != null
}

class EditorViewModel(
    private val container: AppContainer,
    private val photoUri: Uri
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val _transform = MutableStateFlow(WatermarkTransform())
    val transform: StateFlow<WatermarkTransform> = _transform.asStateFlow()

    private var settings: AppSettings = AppSettings()
    private var geocodeJob: Job? = null

    init {
        viewModelScope.launch {
            container.settings.settings.collect { values ->
                settings = values
                _state.update { st ->
                    val template = st.template ?: container.templates.find(values.defaultTemplateId)
                    st.copy(settings = values, template = template)
                }
                rebuildFormatted()
            }
        }
        viewModelScope.launch {
            container.templates.templates.collect { list ->
                _state.update { st ->
                    val current = st.template?.let { cur -> list.firstOrNull { it.id == cur.id } ?: cur }
                    val fallback = list.firstOrNull { it.id == settings.defaultTemplateId } ?: list.firstOrNull()
                    st.copy(templates = list, template = current ?: fallback)
                }
                rebuildFormatted()
            }
        }
        viewModelScope.launch { loadPhoto() }
    }

    private suspend fun loadPhoto() {
        val meta = runCatching { container.photos.readMeta(photoUri) }.getOrNull()
        if (meta == null) {
            _state.update { it.copy(loading = false, error = "无法读取照片信息，请换一张试试") }
            return
        }
        val preview = container.photos.loadPreview(photoUri, 1800)
        if (preview == null) {
            _state.update { it.copy(loading = false, error = "无法解码这张照片，请换一张试试") }
            return
        }
        val device = buildString {
            val make = meta.make
            val model = meta.model
            append(
                when {
                    model.isBlank() -> make
                    make.isBlank() -> model
                    model.startsWith(make, ignoreCase = true) -> model
                    else -> "$make $model"
                }
            )
            if (meta.lens.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(meta.lens)
            }
        }
        val source = WatermarkSource(
            dateTime = meta.dateTime,
            latitude = meta.latitude,
            longitude = meta.longitude,
            altitude = meta.altitude,
            device = device,
            brand = "迹印相机"
        )
        _state.update {
            it.copy(loading = false, preview = preview, meta = meta, source = source)
        }
        rebuildFormatted()
        val lat = meta.latitude
        val lon = meta.longitude
        if (lat != null && lon != null) {
            refreshAddress()
            if (settings.autoWeather) loadWeather(lat, lon, meta.dateTime)
        }
    }

    private fun rebuildFormatted() {
        val st = _state.value
        val template = st.template ?: return
        val formatted = WatermarkFormatter.format(st.source, template, st.settings.coordSystem)
        _state.update { it.copy(formatted = formatted) }
    }

    fun selectTemplate(id: String) {
        val template = _state.value.templates.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(template = template) }
        rebuildFormatted()
    }

    fun updateFields(transform: (FieldConfig) -> FieldConfig) {
        _state.update { st ->
            val template = st.template ?: return@update st
            st.copy(template = template.copy(fields = transform(template.fields)))
        }
        rebuildFormatted()
    }

    fun updateStyle(transform: (TemplateStyle) -> TemplateStyle) {
        _state.update { st ->
            val template = st.template ?: return@update st
            st.copy(template = template.copy(style = transform(template.style)))
        }
    }

    fun setAnchor(anchor: Anchor) = updateStyle { it.copy(anchor = anchor) }

    fun setNote(value: String) {
        _state.update { it.copy(source = it.source.copy(note = value)) }
        rebuildFormatted()
    }

    fun setAddress(value: String) {
        _state.update { it.copy(source = it.source.copy(address = value)) }
        rebuildFormatted()
    }

    fun refreshAddress(providerId: String? = null) {
        val st = _state.value
        val lat = st.source.latitude ?: return
        val lon = st.source.longitude ?: return
        geocodeJob?.cancel()
        geocodeJob = viewModelScope.launch {
            _state.update { it.copy(addressLoading = true, addressError = null) }
            if (providerId != null) {
                container.settings.update { it.copy(geoProvider = providerId) }
                settings = settings.copy(geoProvider = providerId)
            }
            val outcome = container.geo.reverse(lat, lon, settings)
            val result = outcome.result
            if (result == null) {
                _state.update {
                    it.copy(
                        addressLoading = false,
                        addressError = outcome.errors.firstOrNull() ?: "没有解析到地址，请检查网络或填写 Key"
                    )
                }
                return@launch
            }
            _state.update {
                it.copy(
                    addressLoading = false,
                    addressError = null,
                    providerName = result.providerName,
                    precision = result.precision,
                    candidates = result.pois,
                    source = it.source.copy(
                        address = result.address,
                        addressSecondary = result.addressShort
                    )
                )
            }
            rebuildFormatted()
        }
    }

    fun choosePoi(poi: GeoPoi) {
        val display = listOfNotNull(
            poi.name.trim().ifBlank { null },
            poi.address?.trim()?.ifBlank { null }
        ).distinct().joinToString("，")
        if (display.isBlank()) return
        _state.update { it.copy(source = it.source.copy(address = display)) }
        rebuildFormatted()
    }

    fun refreshWeather() {
        val st = _state.value
        val lat = st.source.latitude ?: return
        val lon = st.source.longitude ?: return
        loadWeather(lat, lon, st.source.dateTime ?: LocalDateTime.now())
    }

    private fun loadWeather(lat: Double, lon: Double, date: LocalDateTime) {
        viewModelScope.launch {
            val weather = container.weather.weatherAt(lat, lon, date) ?: return@launch
            val value = listOfNotNull(
                weather.text.ifBlank { null },
                weather.temperatureC?.let { "$it°C" }
            ).joinToString(" ")
            _state.update { it.copy(source = it.source.copy(weather = value)) }
            rebuildFormatted()
        }
    }

    fun useCurrentTime() {
        _state.update { it.copy(source = it.source.copy(dateTime = LocalDateTime.now())) }
        rebuildFormatted()
    }

    fun onGesture(dx: Float, dy: Float, zoom: Float, rotation: Float) {
        _transform.update { base ->
            WatermarkTransform(
                offsetX = (base.offsetX + dx).coerceIn(-1.2f, 1.2f),
                offsetY = (base.offsetY + dy).coerceIn(-1.2f, 1.2f),
                scale = (base.scale * zoom).coerceIn(0.4f, 2.5f),
                rotation = normalize(base.rotation + rotation)
            )
        }
    }

    fun setScale(scale: Float) {
        _transform.update { it.copy(scale = scale.coerceIn(0.4f, 2.5f)) }
    }

    fun resetTransform() {
        _transform.value = WatermarkTransform()
    }

    fun export() {
        val st = _state.value
        val template = st.template ?: return
        val text = st.formatted ?: return
        if (st.exporting) return
        viewModelScope.launch {
            _state.update { it.copy(exporting = true, exportProgress = 0f, message = null) }
            try {
                val result = container.exporter.export(
                    ExportRequest(
                        sourceUri = photoUri,
                        template = template,
                        text = text,
                        transform = _transform.value,
                        maxSide = st.settings.exportMaxSide,
                        jpegQuality = st.settings.jpegQuality,
                        keepExif = st.settings.keepExif,
                        exifComment = buildExifComment(st),
                        fileNameHint = st.meta?.displayName.orEmpty()
                    )
                ) { progress ->
                    _state.update { it.copy(exportProgress = progress) }
                }
                _state.update {
                    it.copy(exporting = false, exported = result, message = "已保存到相册 Pictures/TraceMark")
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(exporting = false, message = "导出失败：${t.message ?: "未知错误"}")
                }
            }
        }
    }

    private fun buildExifComment(st: EditorUiState): String {
        val time = st.source.dateTime?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).orEmpty()
        return listOf(
            "迹印相机",
            st.formatted?.latValue?.takeIf { it.isNotBlank() }?.let { "纬度 $it" },
            st.formatted?.lngValue?.takeIf { it.isNotBlank() }?.let { "经度 $it" },
            st.source.address.ifBlank { null },
            time.ifBlank { null },
            st.formatted?.code?.takeIf { it.isNotBlank() }?.let { "防伪码 $it" }
        ).filterNotNull().joinToString(" | ")
    }

    fun clearExportResult() {
        _state.update { it.copy(exported = null) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun normalize(degrees: Float): Float {
        var value = degrees % 360f
        if (value > 180f) value -= 360f
        if (value < -180f) value += 360f
        return value
    }

    companion object {
        fun factory(container: AppContainer, uri: Uri) = viewModelFactory {
            initializer { EditorViewModel(container, uri) }
        }
    }
}
