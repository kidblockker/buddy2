package com.buddy.app.camera

import android.content.Context
import android.graphics.*
import android.util.Base64
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class CameraHelper(private val context: Context) {
    private var imageCapture: ImageCapture? = null
    private var provider: ProcessCameraProvider? = null
    private val executor = Executors.newSingleThreadExecutor()

    fun startCamera(owner: LifecycleOwner, preview: PreviewView, onReady: () -> Unit) {
        ProcessCameraProvider.getInstance(context).also { future ->
            future.addListener({
                provider = future.get()
                val prev = Preview.Builder().build().apply { setSurfaceProvider(preview.surfaceProvider) }
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                try {
                    provider?.unbindAll()
                    provider?.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, prev, imageCapture)
                    onReady()
                } catch (_: Exception) {}
            }, ContextCompat.getMainExecutor(context))
        }
    }

    fun capturePhoto(onCaptured: (String) -> Unit, onError: (String) -> Unit) {
        val cap = imageCapture ?: run { onError("Camera not ready"); return }
        cap.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val b64 = toBase64(image); image.close(); onCaptured(b64)
                } catch (e: Exception) { image.close(); onError(e.message ?: "Error") }
            }
            override fun onError(e: ImageCaptureException) = onError(e.message ?: "Capture failed")
        })
    }

    private fun toBase64(image: ImageProxy): String {
        val buf = image.planes[0].buffer
        val bytes = ByteArray(buf.remaining()).also { buf.get(it) }
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val rot = image.imageInfo.rotationDegrees
        if (rot != 0) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height,
            Matrix().apply { postRotate(rot.toFloat()) }, true)
        val max = 1024
        if (bmp.width > max || bmp.height > max) {
            val s = max.toFloat() / maxOf(bmp.width, bmp.height)
            bmp = Bitmap.createScaledBitmap(bmp, (bmp.width*s).toInt(), (bmp.height*s).toInt(), true)
        }
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            .toByteArray().let { Base64.encodeToString(it, Base64.NO_WRAP) }
    }

    fun stopCamera() = provider?.unbindAll()
    fun destroy()    = executor.shutdown()
}
