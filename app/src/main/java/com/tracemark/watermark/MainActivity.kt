package com.tracemark.watermark

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.tracemark.watermark.ui.TraceMarkRoot

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestLegacyStorageIfNeeded()
        val initialUri = extractSharedImage(intent)
        setContent {
            TraceMarkRoot(container = container, initialUri = initialUri)
        }
    }

    private fun requestLegacyStorageIfNeeded() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) return
        val needed = listOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        needed.forEach { permission -> permissionLauncher.launch(permission) }
    }

    private fun extractSharedImage(intent: Intent?): Uri? {
        if (intent == null) return null
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_VIEW) return null
        return IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            ?: if (intent.action == Intent.ACTION_VIEW) intent.data else null
    }
}
