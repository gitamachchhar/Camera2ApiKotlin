package com.camera2kotlin.widgets

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.NonNull
import com.camera2kotlin.R
import java.util.Vector

class Thumb private constructor() {

    var index: Int = 0
        private set
    var `val`: Float = 0.toFloat()
    var pos: Float = 0.toFloat()
    var bitmap: Bitmap? = null
        private set(@NonNull bitmap) {
            field = bitmap
            widthBitmap = bitmap!!.getWidth()
            heightBitmap = bitmap.getHeight()
        }
    var widthBitmap: Int = 0
        private set
    private var heightBitmap: Int = 0

    var lastTouchX: Float = 0.toFloat()

    init {
        `val` = 0f
        pos = 0f
    }

    companion object {

        val LEFT = 0
        val RIGHT = 1

        @NonNull
        fun initThumbs(resources: Resources): List<Thumb> {

            val thumbs = Vector<Thumb>()

            for (i in 0..1) {
                val th = Thumb()
                th.index = i
                if (i == 0) {
                    val resImageLeft = R.drawable.apptheme_text_select_handle_left
                    th.bitmap = BitmapFactory.decodeResource(resources, resImageLeft)
                } else {
                    val resImageRight = R.drawable.apptheme_text_select_handle_right
                    th.bitmap = BitmapFactory.decodeResource(resources, resImageRight)
                }

                thumbs.add(th)
            }

            return thumbs
        }

        fun getWidthBitmap(@NonNull thumbs: List<Thumb>): Int {
            return thumbs[0].widthBitmap
        }

        fun getHeightBitmap(@NonNull thumbs: List<Thumb>): Int {
            return thumbs[0].heightBitmap
        }
    }
}
