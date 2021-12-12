package com.aiden.tflite.tliteimagetransformer.util

import android.graphics.*
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ImageHelper {
    fun decodeBitmap(file: File): Bitmap {
        // First, decode EXIF data and retrieve transformation matrix
        val exif = ExifInterface(file.absolutePath)
        val transformation =
            decodeExifOrientation(
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90
                )
            )

        // Read bitmap using factory methods, and transform it using EXIF data
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        return Bitmap.createBitmap(
            BitmapFactory.decodeFile(file.absolutePath),
            0, 0, bitmap.width, bitmap.height, transformation, true
        )
    }

    private fun decodeExifOrientation(orientation: Int): Matrix {
        val matrix = Matrix()

        // Apply transformation corresponding to declared EXIF orientation
        when (orientation) {
            ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED -> Unit
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90F)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180F)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270F)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1F, 1F)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1F, -1F)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postScale(-1F, 1F)
                matrix.postRotate(270F)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postScale(-1F, 1F)
                matrix.postRotate(90F)
            }

            // Error out if the EXIF orientation is invalid
            else -> throw IllegalArgumentException("Invalid orientation: $orientation")
        }

        // Return the resulting matrix
        return matrix
    }

    fun createCenterCropBitmap(bitmap: Bitmap): Bitmap {
        return if (bitmap.width >= bitmap.height) {
            Bitmap.createBitmap(
                bitmap,
                bitmap.width / 2 - bitmap.height / 2,
                0,
                bitmap.height,
                bitmap.height
            )
        } else {
            Bitmap.createBitmap(
                bitmap,
                0,
                bitmap.height / 2 - bitmap.width / 2,
                bitmap.width,
                bitmap.width
            )
        }
    }

    fun createEmptyBitmap(imageWidth: Int, imageHeight: Int, color: Int = 0): Bitmap {
        val ret = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.RGB_565)
        if (color != 0) {
            ret.eraseColor(color)
        }
        return ret
    }

    fun bitmapToByteBuffer(
        bitmapIn: Bitmap,
        width: Int,
        height: Int,
        mean: Float = 0.0f,
        std: Float = 255.0f
    ): ByteBuffer {
        val bitmap = scaleBitmapAndKeepRatio(bitmapIn, width, height)
        val inputImage = ByteBuffer.allocateDirect(1 * width * height * 3 * 4)
        inputImage.order(ByteOrder.nativeOrder())
        inputImage.rewind()

        val intValues = IntArray(width * height)
        bitmap.getPixels(intValues, 0, width, 0, 0, width, height)
        var pixel = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val value = intValues[pixel++]

                // Normalize channel values to [-1.0, 1.0]. This requirement varies by
                // model. For example, some models might require values to be normalized
                // to the range [0.0, 1.0] instead.
                inputImage.putFloat(((value shr 16 and 0xFF) - mean) / std)
                inputImage.putFloat(((value shr 8 and 0xFF) - mean) / std)
                inputImage.putFloat(((value and 0xFF) - mean) / std)
            }
        }

        inputImage.rewind()
        return inputImage
    }

    fun scaleBitmapAndKeepRatio(
        targetBmp: Bitmap,
        reqHeightInPixels: Int,
        reqWidthInPixels: Int
    ): Bitmap {
        if (targetBmp.height == reqHeightInPixels && targetBmp.width == reqWidthInPixels) {
            return targetBmp
        }
        val matrix = Matrix()
        matrix.setRectToRect(
            RectF(
                0f, 0f,
                targetBmp.width.toFloat(),
                targetBmp.width.toFloat()
            ),
            RectF(
                0f, 0f,
                reqWidthInPixels.toFloat(),
                reqHeightInPixels.toFloat()
            ),
            Matrix.ScaleToFit.FILL
        )
        return Bitmap.createBitmap(
            targetBmp, 0, 0,
            targetBmp.width,
            targetBmp.width, matrix, true
        )
    }

    fun convertArrayToBitmap(
        imageArray: Array<Array<Array<FloatArray>>>,
        imageWidth: Int,
        imageHeight: Int
    ): Bitmap {
        val conf = Bitmap.Config.ARGB_8888 // see other conf types
        val styledImage = Bitmap.createBitmap(imageWidth, imageHeight, conf)

        for (x in imageArray[0].indices) {
            for (y in imageArray[0][0].indices) {
                val color = Color.rgb(
                    ((imageArray[0][x][y][0] * 255).toInt()),
                    ((imageArray[0][x][y][1] * 255).toInt()),
                    (imageArray[0][x][y][2] * 255).toInt()
                )

                // this y, x is in the correct order!!!
                styledImage.setPixel(y, x, color)
            }
        }
        return styledImage
    }
}