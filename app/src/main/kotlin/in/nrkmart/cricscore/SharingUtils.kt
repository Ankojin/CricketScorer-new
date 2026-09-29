package `in`.nrkmart.cricscore

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

suspend fun shareComposableScreenshot(
    context: Context,
    graphicsLayer: GraphicsLayer,
    fileName: String,
    subject: String = "Cricket League Update",
    shareMessage: String = "Check out this update from Cricket League app! 🏏"
) {
    withContext(Dispatchers.Main) {
        Toast.makeText(context, "Generating High-Quality Image...", Toast.LENGTH_SHORT).show()
    }
    try {
        val imageBitmap = graphicsLayer.toImageBitmap()
        val origWidth = imageBitmap.width
        val origHeight = imageBitmap.height
        
        if (origWidth <= 0 || origHeight <= 0) {
            throw IllegalStateException("Generated bitmap has invalid dimensions: ${origWidth}x${origHeight}")
        }

        // 100% SOFTWARE-BASED extraction to avoid Hardware Bitmap exceptions
        val srcBitmap = Bitmap.createBitmap(origWidth, origHeight, Bitmap.Config.ARGB_8888)
        val buffer = IntArray(origWidth * origHeight)
        imageBitmap.readPixels(buffer)
        srcBitmap.setPixels(buffer, 0, origWidth, 0, 0, origWidth, origHeight)

        // Ultra-HD 2x Supersampling with anti-aliasing for pin-sharp text quality
        val scaleFactor = if (origWidth < 1800) 2.0f else 1.0f
        val targetWidth = (origWidth * scaleFactor).toInt()
        val targetHeight = (origHeight * scaleFactor).toInt()

        val bitmap = if (scaleFactor > 1.0f) {
            val scaled = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(scaled)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val matrix = Matrix().apply { postScale(scaleFactor, scaleFactor) }
            canvas.drawBitmap(srcBitmap, matrix, paint)
            srcBitmap.recycle()
            scaled
        } else {
            srcBitmap
        }

        withContext(Dispatchers.IO) {
            val cachePath = File(context.cacheDir, "shared_images")
            cachePath.mkdirs()
            
            // Purge cached images older than 24 hours to prevent cache bloat
            val dayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000
            cachePath.listFiles()?.forEach { oldFile ->
                if (oldFile.lastModified() < dayAgo) {
                    oldFile.delete()
                }
            }

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
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, shareMessage)
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
