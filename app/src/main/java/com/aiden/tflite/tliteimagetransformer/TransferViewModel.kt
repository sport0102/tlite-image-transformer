package com.aiden.tflite.tliteimagetransformer

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.aiden.tflite.tliteimagetransformer.model.TransferResult
import kotlinx.coroutines.*

class TransferViewModel(application: Application) : AndroidViewModel(application) {
    private val transferExecutor by lazy {
        TransferExecutor(
            application.applicationContext,
            threadCount = 4,
            useGPU = true
        )
    }
    private val _originalImageBitmap = MutableLiveData<Bitmap>()
    val originalImageBitmap: LiveData<Bitmap> get() = _originalImageBitmap
    private val _styleImageBitmap = MutableLiveData<Bitmap>()
    val styleImageBitmap: LiveData<Bitmap> get() = _styleImageBitmap
    private val _transferResult = MutableLiveData<TransferResult>()
    val transferResult: LiveData<TransferResult> get() = _transferResult

    init {
        setTransferStyle(application.applicationContext)
    }

    fun setTransferStyle(applicationContext: Context) {
        val inputStream = applicationContext.assets.open("thumbnails/style19.jpg")
        _styleImageBitmap.value = BitmapFactory.decodeStream(inputStream)
    }

    fun setOriginalImageBitmap(bitmap: Bitmap) = run { _originalImageBitmap.value = bitmap }

    fun transferImage() {
        viewModelScope.launch(Dispatchers.IO) {
            val transferResult = transferExecutor.execute(originalImageBitmap.value, styleImageBitmap.value)
            _transferResult.postValue(transferResult)
        }

    }

    override fun onCleared() {
        transferExecutor.close()
        super.onCleared()
    }
}