package com.tracemark.watermark

import android.app.Application
import android.content.Context
import com.tracemark.watermark.data.PhotoRepository
import com.tracemark.watermark.data.SettingsStore
import com.tracemark.watermark.data.TemplateRepository
import com.tracemark.watermark.data.WeatherRepository
import com.tracemark.watermark.export.PhotoExporter
import com.tracemark.watermark.geo.GeoRepository

class TraceMarkApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settings = SettingsStore(appContext)
    val photos = PhotoRepository(appContext)
    val templates = TemplateRepository(appContext)
    val geo = GeoRepository()
    val weather = WeatherRepository()
    val exporter = PhotoExporter(appContext, photos)
}

val Context.container: AppContainer
    get() = (applicationContext as TraceMarkApp).container
