package com.camera2kotlin.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.AttributeSet
import android.util.LongSparseArray
import android.view.View
import androidx.annotation.NonNull
import com.camera2kotlin.R
import com.camera2kotlin.utils.BackgroundExecutor
import com.camera2kotlin.utils.UiThreadExecutor

class TimeLineView @JvmOverloads constructor(@NonNull context: Context, attrs: AttributeSet, defStyleAttr: Int = 0) :
    View(context, attrs, defStyleAttr) {

    private var mVideoUri: Uri? = null
    private var mHeightView: Int = 0
    private var mBitmapList: LongSparseArray<Bitmap>? = null

    init {
        init()
    }

    private fun init() {
        mHeightView = context.resources.getDimensionPixelOffset(R.dimen.frames_video_height)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val minW = paddingLeft + paddingRight + suggestedMinimumWidth
        val w = View.resolveSizeAndState(minW, widthMeasureSpec, 1)

        val minH = paddingBottom + paddingTop + mHeightView
        val h = View.resolveSizeAndState(minH, heightMeasureSpec, 1)

        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)

        if (w != oldW) {
            getBitmap(w)
        }

    }

    private fun getBitmap(viewWidth: Int) {
        BackgroundExecutor.execute(object : BackgroundExecutor.Task("", 0L, "") {
            override fun execute() {
                try {
                    val thumbnailList = LongSparseArray<Bitmap>()

                    val mediaMetadataRetriever = MediaMetadataRetriever()
                    mediaMetadataRetriever.setDataSource(context, mVideoUri)

                    // Retrieve media data
                    val videoLengthInMs =
                        (Integer.parseInt(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) * 1000).toLong()

                    // Set thumbnail properties (Thumbs are squares)
                    val thumbWidth = mHeightView
                    val thumbHeight = mHeightView

                    val numThumbs = Math.ceil((viewWidth.toFloat() / thumbWidth).toDouble()).toInt()

                    val interval = videoLengthInMs / numThumbs

                    for (i in 0 until numThumbs) {
                        var bitmap = mediaMetadataRetriever.getFrameAtTime(
                            i * interval,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        )

                        try {
                            bitmap = Bitmap.createScaledBitmap(bitmap, thumbWidth, thumbHeight, false)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        thumbnailList.put(i.toLong(), bitmap)
                    }

                    mediaMetadataRetriever.release()
                    returnBitmaps(thumbnailList)
                } catch (e: Throwable) {
                    Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e)
                }

            }
        }
        )
    }

    private fun returnBitmaps(thumbnailList: LongSparseArray<Bitmap>) {
        UiThreadExecutor.runTask("", Runnable {
            mBitmapList = thumbnailList
            invalidate()
        }, 0L)
    }

    override fun onDraw(@NonNull canvas: Canvas) {
        super.onDraw(canvas)
        if (mBitmapList != null) {
            canvas.save()
            var x = 0

            for (i in 0 until mBitmapList!!.size()) {
                val bitmap = mBitmapList!!.get(i.toLong())

                if (bitmap != null) {
                    canvas.drawBitmap(bitmap, x.toFloat(), 0f, null)
                    x = x + bitmap.width
                }
            }
        }
    }

    fun setVideo(@NonNull data: Uri) {
        mVideoUri = data
    }

}
