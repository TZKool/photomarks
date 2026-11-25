package com.tracemark.watermark.data

import android.content.Context
import com.tracemark.watermark.model.BuiltInTemplates
import com.tracemark.watermark.model.WatermarkTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class TemplateRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }
    private val dir = File(context.filesDir, "user_templates").apply { mkdirs() }

    private val _templates = MutableStateFlow(BuiltInTemplates.all)
    val templates: StateFlow<List<WatermarkTemplate>> = _templates.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        val user = dir.listFiles { file -> file.extension == "json" }
            ?.mapNotNull { file ->
                runCatching {
                    json.decodeFromString(WatermarkTemplate.serializer(), file.readText())
                        .copy(builtIn = false)
                }.getOrNull()
            }
            ?.sortedBy { it.name }
            ?: emptyList()
        _templates.value = BuiltInTemplates.all + user
    }

    fun find(id: String?): WatermarkTemplate? = _templates.value.firstOrNull { it.id == id }

    suspend fun save(template: WatermarkTemplate): WatermarkTemplate = withContext(Dispatchers.IO) {
        val id = template.id.ifBlank { "user_${UUID.randomUUID().toString().take(8)}" }
        val stored = template.copy(id = id, builtIn = false)
        File(dir, "$id.json").writeText(json.encodeToString(WatermarkTemplate.serializer(), stored))
        reload()
        stored
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(dir, "$id.json").delete()
        reload()
    }

    suspend fun duplicate(base: WatermarkTemplate, newName: String): WatermarkTemplate {
        val copy = base.copy(
            id = "",
            name = newName,
            description = "自定义模板",
            builtIn = false
        )
        return save(copy)
    }
}
