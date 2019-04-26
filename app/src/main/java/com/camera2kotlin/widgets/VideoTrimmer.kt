package com.camera2kotlin.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Message
import android.os.RecoverySystem
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.annotation.NonNull
import com.camera2kotlin.R
import com.camera2kotlin.interfaces.OnProgressVideoListener
import com.camera2kotlin.interfaces.OnRangeSeekBarListener
import com.camera2kotlin.interfaces.OnTrimVideoListener
import com.camera2kotlin.interfaces.OnVideoListener
import com.camera2kotlin.model.MediaData
import com.camera2kotlin.utils.BackgroundExecutor
import com.camera2kotlin.utils.MediaFileUtils.Companion.stringForTime
import com.camera2kotlin.utils.UiThreadExecutor
import io.realm.Realm
import java.io.File
import java.lang.ref.WeakReference
import java.util.*


class VideoTrimmer : FrameLayout {

    private var mHolderTopView: SeekBar? = null
    private var mRangeSeekBarView: RangeSeekBarView? = null
    private var mLinearVideo: RelativeLayout? = null
    private var mTimeInfoContainer: View? = null
    private var mVideoView: VideoView? = null
    private var mPlayView: ImageView? = null
    private var mTextSize: TextView? = null
    private var mTextTimeFrame: TextView? = null
    private var mTextTime: TextView? = null
    private var mTimeLineView: TimeLineView? = null

    private var mVideoProgressIndicator: ProgressBarView? = null
    private var mListeners: MutableList<OnProgressVideoListener>? = null
    private var mOnTrimVideoListener: OnTrimVideoListener? = null
    private var mOnVideoListener: OnVideoListener? = null

    private var mDuration = 0
    private var mTimeVideo = 0
    private var mStartPosition = 0
    private var mEndPosition = 0

    private var mOriginSizeFile: Long = 0
    private var mResetSeekBar = true
    var isUpdateRequired = false
    private val mMessageHandler = MessageHandler(this)

    private var mData: MediaData? = null
    private var mediaData: MediaData? = null

    @JvmOverloads
    constructor(@NonNull context: Context, attrs: AttributeSet, defStyleAttr: Int = 0) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(context)
    }

    constructor(@NonNull context: Context) : super(context) {
        init(context)
    }

    private fun init(context: Context) {
        LayoutInflater.from(context).inflate(R.layout.videotrimming, this, true)
        init()
        setUpListeners()
        setUpMargins()
    }

    private fun init() {

        mHolderTopView = findViewById(R.id.handlerTop)
        mVideoView = findViewById(R.id.videoView)
        mLinearVideo = findViewById(R.id.layout_surface_view)
        mTextTime = findViewById(R.id.textTime)
        mTextTimeFrame = findViewById(R.id.textTimeSelection)
        mTimeInfoContainer = findViewById(R.id.timeText)
        mRangeSeekBarView = findViewById(R.id.timeLineBar)
        mPlayView = findViewById(R.id.icon_video_play)
        mTimeLineView = findViewById(R.id.timeLineView)
        mTextSize = findViewById(R.id.textSize)
        mVideoProgressIndicator = findViewById(R.id.timeVideoView)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setUpListeners() {

        mListeners = ArrayList()
        mListeners!!.add(object : OnProgressVideoListener {
            override fun updateProgress(time: Int, max: Int, scale: Float) {
                updateVideoProgress(time)
            }
        })

        mListeners!!.add(mVideoProgressIndicator as OnProgressVideoListener)

        val gestureDetector = GestureDetector(context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    onClickVideoPlayPause()
                    return true
                }
            }
        )

        mVideoView!!.setOnErrorListener { _, what, _ ->
            if (mOnTrimVideoListener != null)
                mOnTrimVideoListener!!.onError("Something went wrong reason : $what")
            false
        }

        mVideoView!!.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        mRangeSeekBarView!!.addOnRangeSeekBarListener(object : OnRangeSeekBarListener {
            override fun onCreate(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
                // Do nothing
            }

            override fun onSeek(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
                onSeekThumbs(index, value)
            }

            override fun onSeekStart(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
                // Do nothing
            }

            override fun onSeekStop(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
                onStopSeekThumbs()
                insertMediaInTable()
            }
        })

        mRangeSeekBarView!!.addOnRangeSeekBarListener(mVideoProgressIndicator!!)

        mVideoView!!.setOnPreparedListener { mp -> onVideoPrepared(mp) }

        mVideoView!!.setOnCompletionListener { onVideoCompleted() }

    }

    fun insertMediaInTable() {

        Realm.getDefaultInstance().executeTransactionAsync(Realm.Transaction { realm ->
            if (mediaData != null) {
                realm.insertOrUpdate(mediaData)
            }
        }, Realm.Transaction.OnSuccess { isUpdateRequired = false })
    }

    private fun setUpMargins() {

        val marge = mRangeSeekBarView!!.thumbs!![0].widthBitmap
        val widthSeek = mHolderTopView!!.thumb.minimumWidth / 2

        var lp = mHolderTopView!!.layoutParams as RelativeLayout.LayoutParams
        lp.setMargins(marge - widthSeek, 0, marge - widthSeek, 0)
        mHolderTopView!!.layoutParams = lp

        lp = mTimeLineView!!.layoutParams as RelativeLayout.LayoutParams
        lp.setMargins(marge, 0, marge, 0)
        mTimeLineView!!.layoutParams = lp

        lp = mVideoProgressIndicator!!.layoutParams as RelativeLayout.LayoutParams
        lp.setMargins(marge, 0, marge, 0)
        mVideoProgressIndicator!!.layoutParams = lp
    }

    private fun onClickVideoPlayPause() {

        if (mVideoView!!.isPlaying) {
            mPlayView!!.visibility = View.VISIBLE
            mMessageHandler.removeMessages(SHOW_PROGRESS)
            mVideoView!!.pause()
        } else {
            mPlayView!!.visibility = View.GONE

            if (mResetSeekBar) {
                mResetSeekBar = false
                mVideoView!!.seekTo(mStartPosition)
            }

            mMessageHandler.sendEmptyMessage(SHOW_PROGRESS)
            mVideoView!!.start()
        }

    }

    private fun onVideoPrepared(@NonNull mp: MediaPlayer) {

        // Adjust the size of the video
        // so it fits on the screen

        val videoWidth = mp.videoWidth
        val videoHeight = mp.videoHeight
        val videoProportion = videoWidth.toFloat() / videoHeight.toFloat()
        val screenWidth = mLinearVideo!!.width
        val screenHeight = mLinearVideo!!.height
        val screenProportion = screenWidth.toFloat() / screenHeight.toFloat()
        val lp = mVideoView!!.layoutParams

        if (videoProportion > screenProportion) {
            lp.width = screenWidth
            lp.height = (screenWidth.toFloat() / videoProportion).toInt()
        } else {
            lp.width = (videoProportion * screenHeight.toFloat()).toInt()
            lp.height = screenHeight
        }

        mVideoView!!.layoutParams = lp
        mPlayView!!.visibility = View.VISIBLE
        mDuration = mVideoView!!.duration
        setSeekBarPosition()
        setTimeFrames()
        setTimeVideo(0)

        if (mOnVideoListener != null) {
            mOnVideoListener!!.onVideoPrepared()
        }
    }

    private fun setSeekBarPosition() {

        try {
            if (mData != null) {

                mediaData = Realm.getDefaultInstance().copyFromRealm(mData)

                if (mData!!.startTime > 0) {
                    mediaData!!.startTime = mData!!.startTime
                    mStartPosition = mData!!.startTime
                } else {
                    mediaData!!.startTime = mStartPosition
                    mStartPosition = 0
                }

                if (mData!!.endTime > 0) {
                    mediaData!!.endTime = mData!!.endTime
                    mEndPosition = mData!!.endTime
                } else {
                    mediaData!!.endTime = mEndPosition
                    mEndPosition = mDuration
                }

                if (mData!!.mDuration > 0) {
                    mediaData!!.mDuration = mData!!.mDuration
                    mTimeVideo = mData!!.mDuration
                } else {
                    mediaData!!.mDuration = mDuration
                    mTimeVideo = mDuration
                }

                mediaData!!.isTrimmed = mData!!.isTrimmed

            }

            mVideoView!!.seekTo(mStartPosition)
            mRangeSeekBarView!!.initMaxWidth()

            if (mediaData!!.isTrimmed)
                mRangeSeekBarView!!.setThumbPosition(mediaData!!)

        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun onSeekThumbs(index: Int, value: Float) {

        when (index) {

            Thumb.LEFT -> {
                mStartPosition = (mDuration * value / 100L).toInt()
                mVideoView!!.seekTo(mStartPosition)
            }
            Thumb.RIGHT -> {
                mEndPosition = (mDuration * value / 100L).toInt()
            }
        }

        setTimeFrames()
        mTimeVideo = mEndPosition - mStartPosition

        if (mediaData == null) {
            mediaData = Realm.getDefaultInstance().copyFromRealm(mData)
        }

        mediaData!!.startTime = mStartPosition
        mediaData!!.endTime = mEndPosition
        mediaData!!.mDuration = mTimeVideo

        mediaData!!.mThumb1Pos = mRangeSeekBarView!!.getThumbPos(0)

        if (!mRangeSeekBarView!!.fromUser) {
            mediaData!!.mThumb2Pos = mediaData!!.mThumb2Pos
            mRangeSeekBarView!!.fromUser = true
        } else {
            mediaData!!.mThumb2Pos = mRangeSeekBarView!!.getThumbPos(1)
        }

        mediaData!!.isTrimmed = mTimeVideo != mDuration
        isUpdateRequired = true
    }

    private fun setTimeFrames() {
        val seconds = context.getString(R.string.short_seconds)
        mTextTimeFrame!!.text =
                String.format(
                    "%s %s - %s %s",
                    stringForTime(mStartPosition),
                    seconds,
                    stringForTime(mEndPosition),
                    seconds
                )
    }

    private fun setTimeVideo(position: Int) {
        val seconds = context.getString(R.string.short_seconds)
        mTextTime!!.text = String.format("%s %s", stringForTime(position), seconds)
    }

    private fun onStopSeekThumbs() {
        mMessageHandler.removeMessages(SHOW_PROGRESS)
        mVideoView!!.pause()
        mPlayView!!.visibility = View.VISIBLE
    }

    private fun onVideoCompleted() {
        mVideoView!!.seekTo(mStartPosition)
    }

    private fun notifyProgressUpdate(isAll: Boolean) {

        if (mDuration == 0) return

        val position = mVideoView!!.currentPosition
        if (isAll) {
            for (item in mListeners!!) {
                item.updateProgress(position, mDuration, (position * 100 / mDuration).toFloat())
            }
        } else {
            mListeners!![1].updateProgress(position, mDuration, (position * 100 / mDuration).toFloat())
        }
    }

    private fun updateVideoProgress(time: Int) {
        if (mVideoView == null) {
            return
        }

        if (time >= mEndPosition) {
            mMessageHandler.removeMessages(SHOW_PROGRESS)
            mVideoView!!.pause()
            mPlayView!!.visibility = View.VISIBLE
            mResetSeekBar = true
            return
        }
        setTimeVideo(time)
    }


    fun setVideoInformationVisibility(visible: Boolean) {
        mTimeInfoContainer!!.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun setOnTrimVideoListener(onTrimVideoListener: OnTrimVideoListener) {
        mOnTrimVideoListener = onTrimVideoListener
    }

    fun setOnVideoListenre(onVideoListener: OnVideoListener) {
        mOnVideoListener = onVideoListener
    }

    fun setDestinationPath(finalPath: String) {
        val mFinalPath = finalPath
        Log.d(TAG, "Setting custom path $mFinalPath")
    }

    fun destroy() {
        BackgroundExecutor.cancelAll("", true)
        UiThreadExecutor.cancelAll("")
    }

    fun setMaxDuration(maxDuration: Int) {
        val mMaxDuration = maxDuration * 1000
    }

    fun setVideoURI(videoURI: Uri?) {

        if (videoURI == null)
            return


        if (mOriginSizeFile == 0L) {
            val file = File(videoURI.path)

            mOriginSizeFile = file.length()
            val fileSizeInKB = mOriginSizeFile / 1024

            if (fileSizeInKB > 1000) {
                val fileSizeInMB = fileSizeInKB / 1024
                mTextSize!!.text = String.format("%s %s", fileSizeInMB, context.getString(R.string.megabyte))
            } else {
                mTextSize!!.text = String.format("%s %s", fileSizeInKB, context.getString(R.string.kilobyte))
            }
        }

        mVideoView!!.setVideoURI(videoURI)
        mVideoView!!.requestFocus()
        mTimeLineView!!.setVideo(videoURI)

    }

    fun setMediaData(mData: MediaData) {
        this.mData = mData
    }

    private class MessageHandler internal constructor(view: VideoTrimmer) : Handler() {

        @NonNull
        private val mView: WeakReference<VideoTrimmer>

        init {
            mView = WeakReference(view)
        }

        override fun handleMessage(msg: Message) {
            val view = mView.get()
            if (view?.mVideoView == null) {
                return
            }

            view.notifyProgressUpdate(true)
            if (view.mVideoView!!.isPlaying) {
                sendEmptyMessageDelayed(0, 10)
            }
        }
    }

    companion object {

        private val TAG = VideoTrimmer::class.java.simpleName
        private const val SHOW_PROGRESS = 2
    }
}
