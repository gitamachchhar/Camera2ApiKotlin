package com.camera2kotlin.interfaces

interface MediaSelectionCallback {
    fun onSelection(path: String)
    fun setSelectionButtonVisibility(size: Int)
}