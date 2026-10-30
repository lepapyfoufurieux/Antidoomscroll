package com.antiscrolling.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Icône de l'appli, chargée hors du thread principal ; une case vide tant qu'elle charge ou si l'appli a disparu. */
@Composable
fun AppIcon(packageName: String) {
    val pm = LocalContext.current.packageManager
    @SuppressLint("ProduceStateDoesNotAssignValue") // assignée plus bas, faux positif de lint
    val icon by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { pm.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }
    val bitmap = icon
    if (bitmap == null) Box(Modifier.size(40.dp)) else Image(bitmap, contentDescription = null, modifier = Modifier.size(40.dp))
}
