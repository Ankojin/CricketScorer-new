package com.example.cricketscorer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

suspend fun shareComposableScreenshot(context: Context, graphicsLayer: GraphicsLayer, fileName: String) {
    withContext(Dispatchers.Main) {
        Toast.makeText(context, "Generating High-Quality Image...", Toast.LENGTH_SHORT).show()
    }
    try {
        val imageBitmap = graphicsLayer.toImageBitmap()
        val width = imageBitmap.width
        val height = imageBitmap.height
        
        if (width <= 0 || height <= 0) {
            throw IllegalStateException("Generated bitmap has invalid dimensions: ${width}x${height}")
        }

        // 100% SOFTWARE-BASED extraction to avoid Hardware Bitmap exceptions
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val buffer = IntArray(width * height)
        imageBitmap.readPixels(buffer)
        bitmap.setPixels(buffer, 0, width, 0, 0, width, height)

        withContext(Dispatchers.IO) {
            val cachePath = File(context.cacheDir, "shared_images")
            cachePath.mkdirs()
            val file = File(cachePath, "${fileName.replace(" ", "_").lowercase()}_${System.currentTimeMillis()}.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "Cricket Match Statistics")
                putExtra(Intent.EXTRA_TEXT, "Check out these match stats from Cricket Scorer app!")
            }
            
            withContext(Dispatchers.Main) {
                val chooser = Intent.createChooser(shareIntent, "Share $fileName via")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                Toast.makeText(context, "Image Ready for Sharing!", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Failed to generate image: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
