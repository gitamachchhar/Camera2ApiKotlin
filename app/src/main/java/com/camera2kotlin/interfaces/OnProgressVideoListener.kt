package com.camera2kotlin.interfaces

interface OnProgressVideoListener {

    fun updateProgress(time: Int, max: Int, scale: Float)
}