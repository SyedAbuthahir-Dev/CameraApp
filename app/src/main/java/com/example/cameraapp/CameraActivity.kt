package com.example.cameraapp

import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.DisplayMetrics
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.R
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.cameraapp.databinding.ActivityCameraBinding
import com.example.cameraapp.utils.ClassifierListener
import com.example.cameraapp.utils.ImageClassifierHelper
import com.example.cameraapp.utils.hasCameraPermission
import com.example.cameraapp.utils.showDialogPermission
import com.google.common.util.concurrent.ListenableFuture
import org.tensorflow.lite.task.vision.classifier.Classifications
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity(), ClassifierListener {

    private val binding: ActivityCameraBinding by lazy {
        ActivityCameraBinding.inflate(
            layoutInflater
        )
    }
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraSelector: CameraSelector? = null
    private var executor: ExecutorService? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null
    private lateinit var bitmapBuffer: Bitmap

    private lateinit var imageClassifierHelper: ImageClassifierHelper
    private val requestCamera by lazy {
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) {
                Toast.makeText(this, "Camera Permission Granted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestCamera.launch(android.Manifest.permission.CAMERA)
        setContentView(binding.root)

        if (this.hasCameraPermission()) {
            cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            executor = Executors.newSingleThreadExecutor()
            imageClassifierHelper =
                ImageClassifierHelper(context = this, imageClassifierListener = this)
            cameraProviderFuture?.addListener({
                cameraProvider = cameraProviderFuture?.get()
                bindCamera()
            }, ContextCompat.getMainExecutor(this))
        } else {
            showDialogPermission(
                "camera",
                !shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)
            ) { requestCamera.launch(android.Manifest.permission.CAMERA) }
        }

        binding.uiBtCaptureImage.setOnClickListener {
            takePicture()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor?.shutdown()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun bindCamera() {
        val preview: Preview = Preview.Builder().build()
        cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()
        imageCapture = ImageCapture
            .Builder()
            .build()
        imageAnalyzer = ImageAnalysis
            .Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                val displayMetrics = DisplayMetrics()
                val display = this.display
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    display?.getRealMetrics(displayMetrics)
                } else {
                    @Suppress("DEPRECATION")
                    display?.getMetrics(displayMetrics)
                }

                executor?.let { it1 ->
                    it.setAnalyzer(it1){ image->
                        onFaceDetected(image,display?.rotation)
                    }
                }
            }
        preview.setSurfaceProvider(binding.uiCameraPreview.surfaceProvider)
        cameraProvider?.unbindAll()
        cameraProvider?.bindToLifecycle(
            this as LifecycleOwner,
            cameraSelector!!, preview, imageCapture,imageAnalyzer
        )
    }

    private fun onFaceDetected(image: ImageProxy, rotation: Int?) {
            if (!::bitmapBuffer.isInitialized) {
                // The image rotation and RGB image buffer are initialized only once
                // the analyzer has started running
                bitmapBuffer = Bitmap.createBitmap(
                    image.width,
                    image.height,
                    Bitmap.Config.ARGB_8888
                )
            }
        if (rotation != null) {
            classifyImage(image,rotation)
        }
    }

    private fun classifyImage(image: ImageProxy,rotation : Int) {
        // Copy out RGB bits to the shared bitmap buffer
        image.use {
            val buffer = image.planes[0].buffer
            buffer.rewind()
            bitmapBuffer.copyPixelsFromBuffer(buffer)
        }
        imageClassifierHelper.classify(bitmapBuffer, rotation)
    }

    private fun takePicture() {
        val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "photo.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
        executor?.let {
            imageCapture?.takePicture(
                outputOptions,
                it,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        Log.e("imageSavedLocation", outputFileResults.savedUri?.path.toString())
                    }

                    override fun onError(exception: ImageCaptureException) {

                    }
                })
        }
    }

    override fun onError(error: String) {

    }

    override fun onResults(results: List<Classifications>?, inferenceTime: Long, image: Bitmap) {
        this.runOnUiThread {
            findCategoryAndIndex(results,image)
        }
    }

    private fun findCategoryAndIndex(results: List<Classifications>?, image: Bitmap) {
        if (!results.isNullOrEmpty()) {
            if (results[0].categories.isNotEmpty()) {
                if (results[0].categories[0].index == 0 && results[0].categories[0].score >= 0.99) {
                    Log.d("specs", "findCategoryAndIndex: index: ${results[0].categories?.get(0)?.index} - score: ${results[0].categories?.get(0)?.score}")

                }
            }
        }
    }
}