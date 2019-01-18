package com.camera2kotlin.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.NonNull
import androidx.core.content.ContextCompat
import com.camera2kotlin.R
import com.camera2kotlin.interfaces.OnRangeSeekBarListener
import com.camera2kotlin.model.MediaData
import java.util.*

class RangeSeekBarView @JvmOverloads constructor(
    @NonNull context: Context, attrs: AttributeSet,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var mHeightTimeLine: Int = 0
    var thumbs: List<Thumb>? = null
        private set
    private var mListeners: MutableList<OnRangeSeekBarListener>? = null
    private var mMaxWidth: Float = 0.toFloat()
    private var mThumbWidth: Float = 0.toFloat()
    private var mThumbHeight: Float = 0.toFloat()
    private var mViewWidth: Float = 0.toFloat()
    private var mPixelRangeMin: Float = 0.toFloat()
    private var mPixelRangeMax: Float = 0.toFloat()
    private var mScaleRangeMax: Float = 0.toFloat()
    private var mFirstRun: Boolean = false
    var fromUser: Boolean = false

    private val mShadow = Paint()
    private val mLine = Paint()
    private var currentThumb = 0

    init {
        init()
    }

    private fun init() {

        thumbs = Thumb.initThumbs(resources)
        mThumbWidth = Thumb.getWidthBitmap(thumbs!!).toFloat()
        mThumbHeight = Thumb.getHeightBitmap(thumbs!!).toFloat()

        mScaleRangeMax = 100f
        mHeightTimeLine = context.resources.getDimensionPixelOffset(R.dimen.frames_video_height)

        isFocusable = true
        isFocusableInTouchMode = true

        mFirstRun = true

        val shadowColor = ContextCompat.getColor(context, R.color.shadow_color)
        mShadow.isAntiAlias = true
        mShadow.color = shadowColor
        mShadow.alpha = 177

        val lineColor = ContextCompat.getColor(context, R.color.line_color)
        mLine.isAntiAlias = true
        mLine.color = lineColor
        mLine.alpha = 200
    }

    fun initMaxWidth() {
        mMaxWidth = thumbs!![1].pos - thumbs!![0].pos
        onSeekStop(this, 0, thumbs!![0].`val`)
        onSeekStop(this, 1, thumbs!![1].`val`)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val minW = paddingLeft + paddingRight + suggestedMinimumWidth
        mViewWidth = View.resolveSizeAndState(minW, widthMeasureSpec, 1).toFloat()

        val minH = paddingBottom + paddingTop + mThumbHeight.toInt() + mHeightTimeLine
        val viewHeight = View.resolveSizeAndState(minH, heightMeasureSpec, 1)

        setMeasuredDimension(mViewWidth.toInt(), viewHeight)

        mPixelRangeMin = 0f
        mPixelRangeMax = mViewWidth - mThumbWidth

        if (mFirstRun) {
            for (i in thumbs!!.indices) {
                val th = thumbs!![i]
                th.`val` = mScaleRangeMax * i
                th.pos = mPixelRangeMax * i
            }
            // Fire listener callback
            onCreate(this, currentThumb, getThumbValue(currentThumb))
            mFirstRun = false
        }

    }

    override fun onDraw(@NonNull canvas: Canvas) {
        super.onDraw(canvas)
        drawShadow(canvas)
        drawThumbs(canvas)
    }

    override fun onTouchEvent(@NonNull ev: MotionEvent): Boolean {
        val mThumb: Thumb
        val mThumb2: Thumb
        val coordinate = ev.x
        val action = ev.action

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                // Remember where we started
                currentThumb = getClosestThumb(coordinate)

                if (currentThumb == -1) {
                    return false
                }

                mThumb = thumbs!![currentThumb]
                mThumb.lastTouchX = coordinate
                onSeekStart(this, currentThumb, mThumb.`val`)
                return true
            }
            MotionEvent.ACTION_UP -> {

                if (currentThumb == -1) {
                    return false
                }

                mThumb = thumbs!![currentThumb]
                onSeekStop(this, currentThumb, mThumb.`val`)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                mThumb = thumbs!![currentThumb]
                mThumb2 = thumbs!![if (currentThumb == 0) 1 else 0]
                // Calculate the distance moved
                val dx = coordinate - mThumb.lastTouchX
                val newX = mThumb.pos + dx
                if (currentThumb == 0) {

                    if (newX + mThumb.widthBitmap >= mThumb2.pos) {
                        mThumb.pos = mThumb2.pos - mThumb.widthBitmap
                    } else if (newX <= mPixelRangeMin) {
                        mThumb.pos = mPixelRangeMin
                    } else {
                        //Check if thumb is not out of max width
                        checkPositionThumb(mThumb, mThumb2, dx, true)
                        // Move the object
                        mThumb.pos = mThumb.pos + dx

                        // Remember this touch position for the next move event
                        mThumb.lastTouchX = coordinate
                    }

                } else {

                    if (newX <= mThumb2.pos + mThumb2.widthBitmap) {
                        mThumb.pos = mThumb2.pos + mThumb.widthBitmap
                    } else if (newX >= mPixelRangeMax) {
                        mThumb.pos = mPixelRangeMax
                    } else {
                        //Check if thumb is not out of max width
                        checkPositionThumb(mThumb2, mThumb, dx, false)
                        // Move the object
                        mThumb.pos = mThumb.pos + dx
                        // Remember this touch position for the next move event
                        mThumb.lastTouchX = coordinate
                    }
                }

                setThumbPos(currentThumb, mThumb.pos)

                // Invalidate to request a redraw

                invalidate()
                return true
            }
        }
        return false
    }

    private fun checkPositionThumb(
        @NonNull mThumbLeft: Thumb, @NonNull mThumbRight: Thumb, dx: Float,
        isLeftMove: Boolean
    ) {
        if (isLeftMove && dx < 0) {
            if (mThumbRight.pos - (mThumbLeft.pos + dx) > mMaxWidth) {
                mThumbRight.pos = mThumbLeft.pos + dx + mMaxWidth
                setThumbPos(1, mThumbRight.pos)
            }
        } else if (!isLeftMove && dx > 0) {
            if (mThumbRight.pos + dx - mThumbLeft.pos > mMaxWidth) {
                mThumbLeft.pos = mThumbRight.pos + dx - mMaxWidth
                setThumbPos(0, mThumbLeft.pos)
            }
        }
    }

    fun setThumbPosition(mData: MediaData) {
        fromUser = false
        setThumbPos(0, mData.mThumb1Pos)
        setThumbPos(1, mData.mThumb2Pos)
    }

    private fun pixelToScale(index: Int, pixelValue: Float): Float {
        val scale = pixelValue * 100 / mPixelRangeMax
        if (index == 0) {
            val pxThumb = scale * mThumbWidth / 100
            return scale + pxThumb * 100 / mPixelRangeMax
        } else {
            val pxThumb = (100 - scale) * mThumbWidth / 100
            return scale - pxThumb * 100 / mPixelRangeMax
        }
    }

    private fun calculateThumbValue(index: Int) {
        if (index < thumbs!!.size && !thumbs!!.isEmpty()) {
            val th = thumbs!![index]
            th.`val` = pixelToScale(index, th.pos)
            onSeek(this, index, th.`val`)
        }
    }

    private fun getThumbValue(index: Int): Float {
        return thumbs!![index].`val`
    }

    private fun setThumbPos(index: Int, pos: Float) {
        thumbs!![index].pos = pos
        calculateThumbValue(index)
        // Tell the view we want a complete redraw
        invalidate()
    }

    fun getThumbPos(pos: Int): Float {
        return thumbs!![pos].pos
    }

    private fun getClosestThumb(coordinate: Float): Int {
        var closest = -1
        if (!thumbs!!.isEmpty()) {
            for (i in thumbs!!.indices) {
                // Find thumb closest to x coordinate
                val tcoordinate = thumbs!![i].pos + mThumbWidth
                if (coordinate >= thumbs!![i].pos && coordinate <= tcoordinate) {
                    closest = thumbs!![i].index
                }
            }
        }
        return closest
    }

    private fun drawShadow(@NonNull canvas: Canvas) {
        if (!thumbs!!.isEmpty()) {

            for (th in thumbs!!) {
                if (th.index == 0) {
                    val x = th.pos + paddingLeft
                    if (x > mPixelRangeMin) {
                        val mRect = Rect(mThumbWidth.toInt(), 0, (x + mThumbWidth).toInt(), mHeightTimeLine)
                        canvas.drawRect(mRect, mShadow)
                    }
                } else {
                    val x = th.pos - paddingRight
                    if (x < mPixelRangeMax) {
                        val mRect = Rect(x.toInt(), 0, (mViewWidth - mThumbWidth).toInt(), mHeightTimeLine)
                        canvas.drawRect(mRect, mShadow)
                    }
                }
            }
        }
    }

    private fun drawThumbs(@NonNull canvas: Canvas) {

        val p = Paint()
        p.isAntiAlias = true
        p.isDither = true

        if (!thumbs!!.isEmpty()) {
            for (th in thumbs!!) {
                if (th.index == 0) {
                    canvas.drawBitmap(th.bitmap!!, th.pos + paddingLeft, (paddingTop + mHeightTimeLine).toFloat(), p)
                } else {
                    canvas.drawBitmap(th.bitmap!!, th.pos - paddingRight, (paddingTop + mHeightTimeLine).toFloat(), p)
                }
            }
        }
    }

    fun addOnRangeSeekBarListener(listener: OnRangeSeekBarListener) {

        if (mListeners == null) {
            mListeners = ArrayList<OnRangeSeekBarListener>()
        }

        mListeners!!.add(listener)
    }

    private fun onCreate(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
        if (mListeners == null)
            return

        for (item in mListeners!!) {
            item.onCreate(rangeSeekBarView, index, value)
        }
    }

    private fun onSeek(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
        if (mListeners == null)
            return

        for (item in mListeners!!) {
            item.onSeek(rangeSeekBarView, index, value)
        }
    }

    private fun onSeekStart(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
        if (mListeners == null)
            return

        for (item in mListeners!!) {
            item.onSeekStart(rangeSeekBarView, index, value)
        }
    }

    private fun onSeekStop(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
        if (mListeners == null)
            return

        for (item in mListeners!!) {
            item.onSeekStop(rangeSeekBarView, index, value)
        }
    }
}
