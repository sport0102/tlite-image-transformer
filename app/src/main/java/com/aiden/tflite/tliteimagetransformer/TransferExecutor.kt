package com.aiden.tflite.tliteimagetransformer

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.aiden.tflite.tliteimagetransformer.model.TransferResult
import com.aiden.tflite.tliteimagetransformer.util.ImageHelper
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TransferExecutor(
    context: Context,
    private val threadCount: Int = 4,
    useGPU: Boolean = false,
) {
    private val interpreterPredict: Interpreter
    private val interpreterTransform: Interpreter
    private var gpuDelegate: GpuDelegate? = null

    init {
        if (useGPU) {
            interpreterPredict = getInterpreter(context, PREDICTION_FLOAT16_MODEL, true)
            interpreterTransform = getInterpreter(context, TRANSFER_FLOAT16_MODEL, true)
        } else {
            interpreterPredict = getInterpreter(context, PREDICTION_INT8_MODEL, false)
            interpreterTransform = getInterpreter(context, TRANSFER_INT8_MODEL, false)
        }
    }

    private fun getInterpreter(
        context: Context,
        modelName: String,
        useGpu: Boolean = false
    ): Interpreter {
        val tfLiteOptions = Interpreter.Options()
        tfLiteOptions.setNumThreads(threadCount)

        gpuDelegate = null
        if (useGpu) {
            gpuDelegate = GpuDelegate()
            tfLiteOptions.addDelegate(gpuDelegate)
        }

        tfLiteOptions.setNumThreads(threadCount)
        return Interpreter(loadModelFile(context, modelName), tfLiteOptions)
    }

    private fun loadModelFile(context: Context, modelFile: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelFile)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        val retFile = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        fileDescriptor.close()
        return retFile
    }

    fun execute(
        originalBitmap: Bitmap?,
        styleBitmap: Bitmap?,
    ): TransferResult {
        if (originalBitmap == null || styleBitmap == null) {
            return TransferResult(
                ImageHelper.createEmptyBitmap(CONTENT_IMAGE_SIZE, CONTENT_IMAGE_SIZE),
                errorMessage = "original Bitmap : ${originalBitmap} / style Bitmap : ${styleBitmap}"
            )
        }
        try {
            var fullExecutionTime = SystemClock.uptimeMillis()
            var preProcessTime = SystemClock.uptimeMillis()

            val contentArray = ImageHelper.bitmapToByteBuffer(originalBitmap, CONTENT_IMAGE_SIZE, CONTENT_IMAGE_SIZE)
            val input = ImageHelper.bitmapToByteBuffer(styleBitmap, STYLE_IMAGE_SIZE, STYLE_IMAGE_SIZE)

            val inputsForPredict = arrayOf<Any>(input)
            val outputsForPredict = HashMap<Int, Any>()
            val styleBottleneck = Array(1) { Array(1) { Array(1) { FloatArray(BOTTLENECK_SIZE) } } }
            outputsForPredict[0] = styleBottleneck
            preProcessTime = SystemClock.uptimeMillis() - preProcessTime

            var stylePredictTime = SystemClock.uptimeMillis()
            // The results of this inference could be reused given the style does not change
            // That would be a good practice in case this was applied to a video stream.
            interpreterPredict.runForMultipleInputsOutputs(inputsForPredict, outputsForPredict)
            stylePredictTime = SystemClock.uptimeMillis() - stylePredictTime
            Log.d(TAG, "Style Predict Time to run: $stylePredictTime")

            val inputsForStyleTransfer = arrayOf(contentArray, styleBottleneck)
            val outputsForStyleTransfer = HashMap<Int, Any>()
            val outputImage =
                Array(1) { Array(CONTENT_IMAGE_SIZE) { Array(CONTENT_IMAGE_SIZE) { FloatArray(3) } } }
            outputsForStyleTransfer[0] = outputImage

            var styleTransferTime = SystemClock.uptimeMillis()
            interpreterTransform.runForMultipleInputsOutputs(
                inputsForStyleTransfer,
                outputsForStyleTransfer
            )
            styleTransferTime = SystemClock.uptimeMillis() - styleTransferTime
            Log.d(TAG, "Style apply Time to run: $styleTransferTime")

            var postProcessTime = SystemClock.uptimeMillis()
            val styledImage =
                ImageHelper.convertArrayToBitmap(outputImage, CONTENT_IMAGE_SIZE, CONTENT_IMAGE_SIZE)
            postProcessTime = SystemClock.uptimeMillis() - postProcessTime

            fullExecutionTime = SystemClock.uptimeMillis() - fullExecutionTime
            Log.d(TAG, "Time to run everything: $fullExecutionTime")

            return TransferResult(
                styledImage,
                preProcessTime,
                stylePredictTime,
                styleTransferTime,
                postProcessTime,
                fullExecutionTime,
            )
        } catch (e: Exception) {
            val emptyBitmap =
                ImageHelper.createEmptyBitmap(
                    CONTENT_IMAGE_SIZE,
                    CONTENT_IMAGE_SIZE
                )
            return TransferResult(
                emptyBitmap, errorMessage = e.message!!
            )
        }
    }

    fun close() {
        interpreterPredict.close()
        interpreterTransform.close()
        if (gpuDelegate != null) {
            gpuDelegate!!.close()
        }
    }

    companion object {
        private const val TAG = "TransferExecutor"

        private const val PREDICTION_FLOAT16_MODEL = "prediction_float16.tflite"
        private const val PREDICTION_INT8_MODEL = "prediction_int8.tflite"
        private const val TRANSFER_FLOAT16_MODEL = "transfer_float16.tflite"
        private const val TRANSFER_INT8_MODEL = "transfer_int8.tflite"

        private const val STYLE_IMAGE_SIZE = 256
        private const val CONTENT_IMAGE_SIZE = 384
        private const val BOTTLENECK_SIZE = 100
    }

}