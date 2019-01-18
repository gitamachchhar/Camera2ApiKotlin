package com.camera2kotlin.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.*
import android.view.View.*
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.camera2kotlin.R
import com.camera2kotlin.adapters.GalleryAdapter
import com.camera2kotlin.adapters.HorizontalMediaGalleryAdapter
import com.camera2kotlin.interfaces.MediaSelectionCallback
import com.camera2kotlin.model.MediaData
import com.camera2kotlin.utils.Constants.ISGALLERY
import com.camera2kotlin.utils.Constants.ISIMAGEONLY
import com.camera2kotlin.utils.Constants.ISMULTISELECT
import com.camera2kotlin.utils.Constants.MEDIA_PICTURE
import com.camera2kotlin.utils.Constants.MEDIA_VIDEO
import com.camera2kotlin.utils.MediaFileUtils
import com.camera2kotlin.utils.MediaFileUtils.Companion.formatTimer
import com.camera2kotlin.widgets.AutoFitTextureView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import io.realm.Realm
import io.realm.RealmList
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class CameraGalleryActivity : ProgressActivity(), View.OnClickListener,
    ActivityCompat.OnRequestPermissionsResultCallback, View.OnLongClickListener, MediaSelectionCallback {

    private var cameraFace = BACK_CAMERA
    private var mediaType = MEDIA_PICTURE
    private var mState = STATE_PREVIEW
    private var mWidth: Int = 0
    private var mHeight: Int = 0
    private var mSensorOrientation: Int = 0
    private var pickHeight = 150
    private var recordCounter: Int = 0
    private var mIsRecordingVideo: Boolean = false
    private var mFlashSupported: Boolean = false

    private var mVideoAbsolutePath: String? = null
    private var mCameraId: String? = null

    private var mTextureView: AutoFitTextureView? = null
    private var tvTimer: AppCompatTextView? = null
    private var fileCounter: AppCompatTextView? = null
    private var mTvMediacounter: AppCompatTextView? = null
    private var mBottomSheetBehavior: BottomSheetBehavior? = null
    private var bottomSheet: LinearLayoutCompat? = null
    private var expand_icon: AppCompatImageView? = null
    private var openLargeGallery: AppCompatImageView? = null
    private var sendSelected: RelativeLayout? = null
    private var mIvCaptureMedia: AppCompatImageView? = null
    private var mIvCameraface: AppCompatImageView? = null
    private var bottomCaptionLayout: LinearLayout? = null
    private var topCaptionLayout2: LinearLayout? = null
    private var topCaptionLayout1: LinearLayout? = null
    private var mRvListMedia: RecyclerView? = null

    private var mCaptureSession: CameraCaptureSession? = null
    private var mCameraDevice: CameraDevice? = null
    private var mPreviewSize: Size? = null
    private var mBackgroundThread: HandlerThread? = null
    private var mBackgroundHandler: Handler? = null
    private var mImageReader: ImageReader? = null
    private var mImageFileAbsolutePath: File? = null
    private var mVideoSize: Size? = null
    private var mMediaRecorder: MediaRecorder? = null
    private var mPreviewRequestBuilder: CaptureRequest.Builder? = null
    private var mPreviewRequest: CaptureRequest? = null
    private val mCameraOpenCloseLock = Semaphore(1)
    private var mDetector: GestureDetectorCompat? = null
    private var adapter: GalleryAdapter? = null
    private var mListAdapter: HorizontalMediaGalleryAdapter? = null
    private var updater: Runnable? = null
    private var isGallery: Boolean = false
    private var isImageOnly: Boolean = false
    private var isMultiSelect: Boolean = false

    private val mSurfaceTextureListener = object : TextureView.SurfaceTextureListener {

        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {

            mHeight = height
            mWidth = width

            if (mediaType == MEDIA_PICTURE) {
                openCamera(width, height, cameraFace)
            } else {
                openCameraForVideo(width, height, cameraFace)
            }

        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}

    }

    private val mStateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(@NonNull cameraDevice: CameraDevice) {

            mCameraDevice = cameraDevice

            if (mediaType == MEDIA_PICTURE) {
                mCameraOpenCloseLock.release()
                createCameraPreviewSession()

            } else {
                //                startPreview();
                mCameraOpenCloseLock.release()

                if (null != mTextureView) {
                    configureTransform(mTextureView!!.getWidth(), mTextureView!!.getHeight())
                }

                if (mIsRecordingVideo)
                    startRecordingVideo()
            }
        }

        override fun onDisconnected(@NonNull cameraDevice: CameraDevice) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
        }

        override fun onError(@NonNull cameraDevice: CameraDevice, error: Int) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
            finish()
        }

    }

    private val mCaptureCallback = object : CameraCaptureSession.CaptureCallback() {

        private fun process(result: CaptureResult) {

            when (mState) {
                STATE_PREVIEW -> {
                }
                STATE_WAITING_LOCK -> {

                    val afState = result.get(CaptureResult.CONTROL_AF_STATE)

                    if (afState == null) {
                        captureStillPicture()
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState || CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                        if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN
                            captureStillPicture()
                        } else {
                            runPrecaptureSequence()
                        }
                    }
                }
                STATE_WAITING_PRECAPTURE -> {
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null ||
                        aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                        aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED
                    ) {
                        mState = STATE_WAITING_NON_PRECAPTURE
                    }
                }
                STATE_WAITING_NON_PRECAPTURE -> {
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN
                        captureStillPicture()
                    }
                }
            }
        }

        override fun onCaptureProgressed(
            @NonNull session: CameraCaptureSession,
            @NonNull request: CaptureRequest,
            @NonNull partialResult: CaptureResult
        ) {
            process(partialResult)
        }

        override fun onCaptureCompleted(
            @NonNull session: CameraCaptureSession,
            @NonNull request: CaptureRequest,
            @NonNull result: TotalCaptureResult
        ) {
            process(result)

        }
    }

    private val mOnImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        mBackgroundHandler!!.post(
            ImageSaver(
                reader.acquireLatestImage(),
                mImageFileAbsolutePath
            )
        )
    }

    private fun showToast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.camera_gallery_activity)

        init()

        mBottomSheetBehavior!!.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {

            @SuppressLint("SwitchIntDef")
            override fun onStateChanged(@NonNull bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        mBottomSheetBehavior!!.setPeekHeight(pickHeight)
                        expand_icon!!.animate().rotation(0F).setDuration(500).start()
                    }
                    BottomSheetBehavior.STATE_EXPANDED -> expand_icon!!.animate().rotation(180F).setDuration(500).start()
                }
            }

            override fun onSlide(@NonNull bottomSheet: View, slideOffset: Float) {}
        })

        if (isGallery) {
            expand_icon!!.visibility = INVISIBLE
            mBottomSheetBehavior!!.setState(BottomSheetBehavior.STATE_EXPANDED)

            val params = bottomSheet!!.layoutParams as CoordinatorLayout.LayoutParams
            params.behavior = null

        }

        getImageFilePath()

        val mediaFileList: ArrayList<String>?
        MediaFileUtils.clearFileList()

        if (isImageOnly) {
            mediaFileList = MediaFileUtils.getImageFile(Environment.getExternalStorageDirectory())
        } else {
            mediaFileList = MediaFileUtils.getFile(Environment.getExternalStorageDirectory())
        }

        if (mediaFileList != null) {

            val t1 = TreeSet(FileTimeComparator())

            for (path in mediaFileList) {
                t1.add(path)
            }

            mediaFileList.reverse()

            setupHorizontalGallary(mediaFileList)
            setupGridView(mediaFileList)
        }

        mIvCaptureMedia!!.setOnTouchListener { _, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_UP) {
                if (mIsRecordingVideo) {
                    mIsRecordingVideo = false
                    tvTimer!!.visibility = GONE
                    stopRecordingVideo()
                } else {
                    if (adapter != null)
                        adapter!!.clearFileList()

                    if (mListAdapter != null)
                        mListAdapter!!.clearFileList()

                    setSelectedSendButtonVisibility(0)
                    mIvCaptureMedia!!.setImageResource(R.drawable.capture_button_active)
                    takePicture()
                }
            }

            false
        }
    }




    class FileTimeComparator : Comparator<String> {

        override fun compare(o1: String, o2: String): Int {

            val e1 = File(o1)
            val e2 = File(o2)

            val k = e1.lastModified() - e2.lastModified()

            return if (k > 0) {
                1
            } else if (k == 0L) {
                0
            } else {
                -1
            }

        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun init() {

        isGallery = intent.getBooleanExtra(ISGALLERY, false)
        isImageOnly = intent.getBooleanExtra(ISIMAGEONLY, false)
        isMultiSelect = intent.getBooleanExtra(ISMULTISELECT, false)

        mIvCaptureMedia = findViewById(R.id.captureMedia)
        mIvCameraface = findViewById(R.id.cameraFace)
        openLargeGallery = findViewById(R.id.openLargeGallery)
        expand_icon = findViewById(R.id.expand_icon)
        mRvListMedia = findViewById(R.id.listMedia)
        tvTimer = findViewById(R.id.tvTimer)
        mTextureView = findViewById(R.id.texture)
        bottomCaptionLayout = findViewById(R.id.bottomCaptionLayout)
        val mClMain = findViewById<CoordinatorLayout>(R.id.cl_main)

        val icon_back = findViewById<AppCompatImageView>(R.id.icon_back)
        val submitMedia = findViewById<AppCompatTextView>(R.id.submitMedia)
        topCaptionLayout2 = findViewById(R.id.topCaptionLayout2)
        topCaptionLayout1 = findViewById(R.id.topCaptionLayout1)
        fileCounter = findViewById(R.id.fileCounter)
        sendSelected = findViewById(R.id.sendSelected)
        mTvMediacounter = findViewById(R.id.media_counter)

        pickHeight = MediaFileUtils.convertDpToPixel(resources.getDimension(R.dimen.bottom_sheet_height), this)
        pickHeight = 0

        bottomSheet = findViewById(R.id.bottomSheet)
        mBottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        mBottomSheetBehavior!!.setPeekHeight(pickHeight)
        mBottomSheetBehavior!!.setState(BottomSheetBehavior.STATE_COLLAPSED)

        findViewById<AppCompatImageView>(R.id.ic_back).setOnClickListener(this)
        sendSelected!!.setOnClickListener(this)
        mIvCaptureMedia!!.setOnLongClickListener(this)
        mIvCameraface!!.setOnClickListener(this)
        expand_icon!!.setOnClickListener(this)

        icon_back.setOnClickListener(this)
        submitMedia.setOnClickListener(this)

        mDetector = GestureDetectorCompat(this, GalleryGestureListener())

        mClMain.setOnTouchListener { _, motionEvent ->
            mDetector!!.onTouchEvent(motionEvent)
            true
        }

    }

    internal inner class GalleryGestureListener : GestureDetector.SimpleOnGestureListener() {

        override fun onDown(event: MotionEvent): Boolean {
            return true
        }

        override fun onFling(
            event1: MotionEvent, event2: MotionEvent,
            velocityX: Float, velocityY: Float
        ): Boolean {

            if (event1.y > event2.y) {
                mBottomSheetBehavior!!.setState(if (mBottomSheetBehavior!!.getState() == BottomSheetBehavior.STATE_COLLAPSED) BottomSheetBehavior.STATE_EXPANDED else BottomSheetBehavior.STATE_COLLAPSED)
            }
            return true
        }
    }


    private fun setSelectedSendButtonVisibility(isVisible: Int) {
        if (isVisible > 0) {
            sendSelected!!.visibility = VISIBLE
        } else {
            sendSelected!!.visibility = GONE
        }
    }

    private fun getImageFilePath() {
        val mFileName = "IMG_" + Calendar.getInstance().timeInMillis + ".jpg"
        mImageFileAbsolutePath = File(getExternalFilesDir(null), mFileName)
    }

    private fun setCaptionVisibility(isVisible: Boolean) {

        runOnUiThread {
            try {

                if (isVisible) {
                    bottomCaptionLayout!!.visibility = VISIBLE
                    openLargeGallery!!.visibility = GONE
                    mRvListMedia!!.visibility = GONE
                    mIvCaptureMedia!!.visibility = GONE
                    mIvCameraface!!.visibility = GONE

                } else {
                    bottomCaptionLayout!!.visibility = GONE
                    openLargeGallery!!.visibility = VISIBLE
                    mRvListMedia!!.visibility = VISIBLE
                    mIvCaptureMedia!!.visibility = VISIBLE
                    mIvCameraface!!.visibility = VISIBLE

                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateActionBar(selectedFiles: Int) {

        if (selectedFiles > 0) {
            topCaptionLayout1!!.visibility = GONE
            topCaptionLayout2!!.visibility = VISIBLE
            fileCounter!!.text = selectedFiles.toString() + " File(s) Selected"
        } else {
            fileCounter!!.text = ""
            topCaptionLayout1!!.visibility = VISIBLE
            topCaptionLayout2!!.visibility = GONE
        }

    }

    private fun setupHorizontalGallary(mediaFileList: ArrayList<String>?) {
        val manager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        mRvListMedia!!.layoutManager = manager
        mListAdapter = HorizontalMediaGalleryAdapter(mediaFileList!!, this, this, isMultiSelect)
        mRvListMedia!!.adapter = mListAdapter
    }

    private fun setupGridView(mediaFileList: ArrayList<String>?) {
        adapter = GalleryAdapter(mediaFileList, this, isMultiSelect)
        val fullscreenGallery = findViewById<GridView>(R.id.fullscreenGallery)
        fullscreenGallery.adapter = adapter
        fullscreenGallery.isNestedScrollingEnabled = true
    }

    override fun onResume() {
        super.onResume()

        mediaType = MEDIA_PICTURE
        mState = STATE_PREVIEW

        startBackgroundThread()

        if (mTextureView!!.isAvailable) {
            openCamera(mTextureView!!.width, mTextureView!!.height, cameraFace)
        } else {
            mTextureView!!.surfaceTextureListener = mSurfaceTextureListener
        }

        mIvCaptureMedia!!.setImageResource(R.drawable.capture_button)
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, @NonNull permissions: Array<String>,
        @NonNull grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.size != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, getString(R.string.request_permission), Toast.LENGTH_LONG).show()
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun setUpCameraOutputs(width: Int, height: Int, cameraFace: Int) {
        val activity = this
        val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {

            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)

                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing != null) {
                    if (facing == CameraCharacteristics.LENS_FACING_FRONT && cameraFace == BACK_CAMERA) {
                        continue
                    } else if (facing == CameraCharacteristics.LENS_FACING_BACK && cameraFace == FRONT_CAMERA) {
                        continue
                    }
                }

                val map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                ) ?: continue

                val largest = getFullScreenPreview(map.getOutputSizes(ImageFormat.JPEG), width, height)

                mImageReader = ImageReader.newInstance(
                    largest.width, largest.height,
                    ImageFormat.JPEG, /*maxImages*/1
                )
                mImageReader!!.setOnImageAvailableListener(
                    mOnImageAvailableListener, mBackgroundHandler
                )


                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!

                val displaySize = Point()
                activity.windowManager.defaultDisplay.getSize(displaySize)

                mPreviewSize = getFullScreenPreview(
                    map.getOutputSizes(SurfaceTexture::class.java),
                    width, height
                )

                val orientation = resources.configuration.orientation
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mTextureView!!.setAspectRatio(
                        mPreviewSize!!.width, mPreviewSize!!.height
                    )
                } else {
                    mTextureView!!.setAspectRatio(
                        mPreviewSize!!.height, mPreviewSize!!.width
                    )
                }

                val available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                mFlashSupported = available ?: false
                mCameraId = cameraId
                return
            }

        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: NullPointerException) {
            Toast.makeText(this, getString(R.string.camera_error), Toast.LENGTH_LONG).show()
        }

    }

    private fun getFullScreenPreview(outputSizes: Array<Size>, width: Int, height: Int): Size {

        val outputSizeList = Arrays.asList(*outputSizes)
        var fullScreenSize = outputSizeList[0]

        for (i in outputSizeList.indices) {

            val originalWidth = outputSizeList[i].width
            val originalHeight = outputSizeList[i].height

            val originalRatio = originalWidth.toFloat() / originalHeight.toFloat()
            val requiredRatio: Float

            if (width > height) {
                requiredRatio = width.toFloat() / height
                if (outputSizeList[i].width > width && outputSizeList[i].height > height) {
                    continue
                }
            } else {
                requiredRatio = 1 / (width.toFloat() / height)
                if (outputSizeList[i].width > height && outputSizeList[i].height > width) {
                    continue
                }
            }
            if (originalRatio == requiredRatio) {
                fullScreenSize = outputSizeList[i]
                break
            }
        }

        return if (fullScreenSize.width > 3000) {
            Size(1794, 1080)
        } else {
            fullScreenSize
        }
    }

    private fun openCamera(width: Int, height: Int, cameraFace: Int) {

        setUpCameraOutputs(width, height, cameraFace)
        configureTransform(width, height)
        val activity = this
        val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) !== PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            manager.openCamera(mCameraId!!, mStateCallback, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }

    }

    private fun openCameraForVideo(width: Int, height: Int, cameraFace: Int) {

        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var cameraId = ""

        try {
            if (!mCameraOpenCloseLock.tryAcquire(5000, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }


            for (camId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(camId)

                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing != null) {
                    if (facing == CameraCharacteristics.LENS_FACING_FRONT && cameraFace == BACK_CAMERA) {
                        continue
                    } else if (facing == CameraCharacteristics.LENS_FACING_BACK && cameraFace == FRONT_CAMERA) {
                        continue
                    }
                }
                cameraId = camId
            }


            val characteristics = manager.getCameraCharacteristics(cameraId)
            var map: StreamConfigurationMap? = null
            map = characteristics
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)


            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!

            if (map == null) {
                throw RuntimeException("Cannot get available preview/video sizes")
            }

            mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder::class.java))

            mPreviewSize = getFullScreenPreview(
                map.getOutputSizes(SurfaceTexture::class.java),
                width, height
            )

            val orientation = resources.configuration.orientation
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTextureView!!.setAspectRatio(mPreviewSize!!.width, mPreviewSize!!.height)
            } else {
                mTextureView!!.setAspectRatio(mPreviewSize!!.height, mPreviewSize!!.width)
            }
            configureTransform(width, height)
            mMediaRecorder = MediaRecorder()

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) !== PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            manager.openCamera(cameraId, mStateCallback, null)

        } catch (e: CameraAccessException) {
            Toast.makeText(this, "Cannot access the camera.", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: NullPointerException) {
            Toast.makeText(this, getString(R.string.camera_error), Toast.LENGTH_SHORT).show()

        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.")
        }

    }

    @Throws(IOException::class)
    private fun setUpMediaRecorder() {

        mMediaRecorder = MediaRecorder()

        if (mVideoAbsolutePath == null || mVideoAbsolutePath!!.isEmpty()) {
            mVideoAbsolutePath = getVideoFilePath(this)
        }

        mMediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
        mMediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mMediaRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mMediaRecorder!!.setOutputFile(mVideoAbsolutePath)
        mMediaRecorder!!.setVideoEncodingBitRate(1600 * 1000)
        mMediaRecorder!!.setVideoFrameRate(30)
        mMediaRecorder!!.setVideoSize(mVideoSize!!.width, mVideoSize!!.height)
        mMediaRecorder!!.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mMediaRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

        val rotation = windowManager.defaultDisplay.rotation

        when (mSensorOrientation) {
            SENSOR_ORIENTATION_DEFAULT_DEGREES -> mMediaRecorder!!.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation))
            SENSOR_ORIENTATION_INVERSE_DEGREES -> mMediaRecorder!!.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation))
        }
    }

    private fun getVideoFilePath(context: Context): String {
        val dir = context.getExternalFilesDir(null)
        return ((if (dir == null) "" else dir.absolutePath + "/")
                + System.currentTimeMillis() + ".mp4")
    }


    private fun startRecordingVideo() {
        if (null == mCameraDevice || !mTextureView!!.isAvailable() || null == mPreviewSize) {
            return
        }
        try {
            closePreviewSession()
            setUpMediaRecorder()

            try {
                mMediaRecorder!!.prepare()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            val mSTexture = mTextureView!!.surfaceTexture!!
            mSTexture.setDefaultBufferSize(mPreviewSize!!.width, mPreviewSize!!.height)
            mPreviewRequestBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            val surfaces = ArrayList<Surface>()

            val previewSurface = Surface(mSTexture)
            surfaces.add(previewSurface)
            mPreviewRequestBuilder!!.addTarget(previewSurface)

            val recorderSurface = mMediaRecorder!!.surface
            surfaces.add(recorderSurface)
            mPreviewRequestBuilder!!.addTarget(recorderSurface)

            mCameraDevice!!.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(@NonNull cameraCaptureSession: CameraCaptureSession) {
                    mCaptureSession = cameraCaptureSession
                    updatePreview()

                    runOnUiThread {
                        mIsRecordingVideo = true
                        mMediaRecorder!!.start()
                    }
                }

                override fun onConfigureFailed(@NonNull cameraCaptureSession: CameraCaptureSession) {
                    Toast.makeText(this@CameraGalleryActivity, "Failed", Toast.LENGTH_SHORT).show()
                }
            }, mBackgroundHandler)

        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    private fun stopRecordingVideo() {

        try {

            mCaptureSession!!.stopRepeating()
            mCaptureSession!!.abortCaptures()

            mIsRecordingVideo = false
            mMediaRecorder!!.stop()
            mMediaRecorder!!.reset()
            sendData(mVideoAbsolutePath, MEDIA_VIDEO)

        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != 0) {
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun sendData(path: String?, mediaType: Int) {

        Realm.getDefaultInstance().executeTransactionAsync({ realm ->
            realm.where(MediaData::class.java).findAll().deleteAllFromRealm()
            val mData : MediaData? = null
            mData?.filePath = path!!
            mData?.outPutFilePath = Environment.getExternalStorageDirectory().toString() + File.separator + File(path).name
            mData?.fileType = mediaType
            mData?.isTrimmed = false
            mData?.startTime = 0
            realm.insertOrUpdate(mData)
        }, {
            val i = Intent(this@CameraGalleryActivity, SelectMediaFileActivity::class.java)
            i.putExtra("mode", "SingleSelect")
            i.putExtra(ISIMAGEONLY, isImageOnly)
            startActivityForResult(i, 102)
        }, { error -> Log.e("System out", "On Error " + error.message) })
    }

    private fun sendDataList(path: RealmList<MediaData>) {

        Realm.getDefaultInstance().executeTransactionAsync({ realm ->
            realm.where(MediaData::class.java).findAll().deleteAllFromRealm()
            realm.copyToRealm(path)
        }, {
            val i = Intent(this@CameraGalleryActivity, SelectMediaFileActivity::class.java)
            i.putExtra("mode", "MultiSelect")
            i.putExtra(ISIMAGEONLY, isImageOnly)
            startActivityForResult(i, 102)
        }, { error -> Log.e("System out", "error..." + error.message) })

    }

    fun previewSelectedGalleryMedia(path: String) {

        if (MediaFileUtils.isVideoFile(path)) {
            mediaType = MEDIA_VIDEO
        } else {
            mediaType = MEDIA_PICTURE
        }
        sendData(path, mediaType)
    }

    private fun updatePreview() {
        if (null == mCameraDevice) {
            return
        }
        try {
            setUpCaptureRequestBuilder(mPreviewRequestBuilder!!)
            val thread = HandlerThread("CameraPreview")
            thread.start()
            mCaptureSession!!.setRepeatingRequest(mPreviewRequestBuilder!!.build(), null, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    private fun setUpCaptureRequestBuilder(builder: CaptureRequest.Builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
    }

    private fun closePreviewSession() {
        if (mCaptureSession != null) {
            mCaptureSession!!.close()
            mCaptureSession = null
        }
    }

    private fun closeCamera() {

        try {
            mCameraOpenCloseLock.acquire()
            if (null != mCaptureSession) {
                mCaptureSession!!.close()
                mCaptureSession = null
            }
            if (null != mCameraDevice) {
                mCameraDevice!!.close()
                mCameraDevice = null
            }
            if (null != mImageReader) {
                mImageReader!!.close()
                mImageReader = null
            }
            if (null != mMediaRecorder) {
                mMediaRecorder!!.release()
                mMediaRecorder = null
            }

        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            mCameraOpenCloseLock.release()
        }
    }

    private fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("CameraBackground")
        mBackgroundThread!!.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        mBackgroundThread!!.quitSafely()
        try {
            mBackgroundThread!!.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

    }

    private fun createCameraPreviewSession() {

        try {
            val texture = mTextureView!!.surfaceTexture!!

            texture.setDefaultBufferSize(mPreviewSize!!.width, mPreviewSize!!.height)
            val surface = Surface(texture)
            mPreviewRequestBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            mPreviewRequestBuilder!!.addTarget(surface)

            mCameraDevice!!.createCaptureSession(
                Arrays.asList(surface, mImageReader!!.surface),
                object : CameraCaptureSession.StateCallback() {

                    override fun onConfigured(@NonNull cameraCaptureSession: CameraCaptureSession) {

                        if (null == mCameraDevice) {
                            return
                        }

                        mCaptureSession = cameraCaptureSession
                        try {
                            mPreviewRequestBuilder!!.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_AUTO
                            )
                            setAutoFlash(mPreviewRequestBuilder)
                            mPreviewRequest = mPreviewRequestBuilder!!.build()
                            mCaptureSession!!.setRepeatingRequest(
                                mPreviewRequest!!,
                                mCaptureCallback, mBackgroundHandler
                            )
                        } catch (e: CameraAccessException) {
                            e.printStackTrace()
                        }

                    }

                    override fun onConfigureFailed(
                        @NonNull cameraCaptureSession: CameraCaptureSession
                    ) {
                        showToast("Failed")

                    }
                }, null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    private fun configureTransform(viewWidth: Int, viewHeight: Int) {

        if (null == mTextureView || null == mPreviewSize) {
            return
        }

        val rotation = windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, mPreviewSize!!.height.toFloat(), mPreviewSize!!.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                viewHeight.toFloat() / mPreviewSize!!.height,
                viewWidth.toFloat() / mPreviewSize!!.width
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        mTextureView!!.setTransform(matrix)
    }

    private fun takePicture() {
        lockFocus()
    }

    private fun lockFocus() {
        try {
            mPreviewRequestBuilder!!.set(
                CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_START
            )
            mState = STATE_WAITING_LOCK

            if (cameraFace == BACK_CAMERA) {
                mCaptureSession!!.capture(
                    mPreviewRequestBuilder!!.build(), mCaptureCallback,
                    mBackgroundHandler
                )
            } else if (cameraFace == FRONT_CAMERA) {
                captureStillPicture()
            }

        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    private fun runPrecaptureSequence() {
        try {
            mPreviewRequestBuilder!!.set(
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START
            )
            mState = STATE_WAITING_PRECAPTURE
            mCaptureSession!!.capture(
                mPreviewRequestBuilder!!.build(), mCaptureCallback,
                mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    private fun captureStillPicture() {

        try {

            if (null == mCameraDevice) {
                return
            }

            val captureBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(mImageReader!!.surface)

            captureBuilder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_AUTO
            )
            setAutoFlash(captureBuilder)

            val rotation = windowManager.defaultDisplay.rotation
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation))

            val CaptureCallback = object : CameraCaptureSession.CaptureCallback() {

                override fun onCaptureCompleted(
                    @NonNull session: CameraCaptureSession,
                    @NonNull request: CaptureRequest,
                    @NonNull result: TotalCaptureResult
                ) {

                    setCaptionVisibility(true)
                    unlockFocus()
                    sendData(mImageFileAbsolutePath!!.absolutePath, MEDIA_PICTURE)
                }

                override fun onCaptureFailed(@NonNull session: CameraCaptureSession, @NonNull request: CaptureRequest, @NonNull failure: CaptureFailure) {
                    super.onCaptureFailed(session, request, failure)
                    showToast("Saved: capture failed" + failure.reason)
                }
            }

            mCaptureSession!!.stopRepeating()
            mCaptureSession!!.abortCaptures()
            mCaptureSession!!.capture(captureBuilder.build(), CaptureCallback, null)

        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    private fun getOrientation(rotation: Int): Int {
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360
    }

    private fun unlockFocus() {
        try {
            mPreviewRequestBuilder!!.set(
                CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_CANCEL
            )
            setAutoFlash(mPreviewRequestBuilder)
            mCaptureSession!!.capture(
                mPreviewRequestBuilder!!.build(), mCaptureCallback,
                mBackgroundHandler
            )
            mState = STATE_PREVIEW
            mCaptureSession!!.setRepeatingRequest(
                mPreviewRequest!!, mCaptureCallback,
                mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }


    override fun onLongClick(view: View): Boolean {

        when (view.id) {
            R.id.captureMedia ->

                if (!isImageOnly) {

                    if (adapter != null)
                        adapter!!.clearFileList()

                    if (mListAdapter != null)
                        mListAdapter!!.clearFileList()

                    setSelectedSendButtonVisibility(0)
                    mIvCaptureMedia!!.setImageResource(R.drawable.capture_button_active)

                    mIsRecordingVideo = true
                    mediaType = MEDIA_VIDEO
                    closeCamera()

                    Handler().postDelayed({
                        openCameraForVideo(mWidth, mHeight, cameraFace)
                        updateVideoRecordingTimer()
                    }, 200)


                    recordCounter = 0

                }
        }
        return true
    }

    internal fun updateVideoRecordingTimer() {

        tvTimer!!.visibility = VISIBLE

        val timerHandler = Handler()
        updater = Runnable {
            runOnUiThread { tvTimer!!.setText(formatTimer(recordCounter)) }
            if (mIsRecordingVideo)
                timerHandler.postDelayed(updater, 1000)

            recordCounter++
        }
        timerHandler.post(updater)

    }

    override fun onClick(view: View) {

        when (view.id) {

            R.id.cameraFace -> {

                if (cameraFace == 1) {
                    cameraFace = 0
                } else if (cameraFace == 0) {
                    cameraFace = 1
                }
                closeCamera()
                mIsRecordingVideo = false
                mediaType = MEDIA_PICTURE
                openCamera(mWidth, mHeight, cameraFace)
            }

            R.id.expand_icon -> mBottomSheetBehavior!!.setState(if (mBottomSheetBehavior!!.getState() == BottomSheetBehavior.STATE_COLLAPSED) BottomSheetBehavior.STATE_EXPANDED else BottomSheetBehavior.STATE_COLLAPSED)

            R.id.ic_back -> onBackPressed()

            R.id.icon_back -> {
                updateActionBar(0)
                if (adapter != null)
                    adapter!!.clearFileList()
            }

//            R.id.submitMedia -> sendDataToNextScreen(adapter!!.getSelectedFileList())
//            R.id.sendSelected -> sendDataToNextScreen(mListAdapter!!.getSelectedFilesList())
        }
    }

    override fun onBackPressed() {

       /* if (isGallery) {
            if (adapter != null && adapter!!.getSelectedFileList().size() > 0) {
                adapter!!.clearFileList()
                updateActionBar(0)
            } else {
                finish()
            }
        } else if (mListAdapter != null && mListAdapter!!.getSelectedFilesList().size() > 0) {
            mListAdapter!!.clearFileList()
            setSelectionButtonVisibility(0)
        } else if (adapter != null && adapter!!.getSelectedFileList().size() > 0) {
            adapter!!.clearFileList()
            updateActionBar(0)
        } else if (mBottomSheetBehavior!!.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            mBottomSheetBehavior!!.setState(BottomSheetBehavior.STATE_COLLAPSED)
        } else {
            super.onBackPressed()
        }*/

    }

    private fun sendDataToNextScreen(mFileList: ArrayList<String>) {

        val mediaList = RealmList<MediaData>()

        for (str in mFileList) {

            val media : MediaData? = null
            media?.filePath = str

            if (MediaFileUtils.isVideoFile(str)) {
                media?.fileType = MEDIA_VIDEO
                media?.outPutFilePath = Environment.getExternalStorageDirectory().toString() + File.separator + File(str).name
            } else {
                media?.fileType = MEDIA_PICTURE
                media?.outPutFilePath = Environment.getExternalStorageDirectory().toString() + File.separator + File(str).name
            }
            mediaList.add(media)
        }
        sendDataList(mediaList)
    }

    private fun setAutoFlash(requestBuilder: CaptureRequest.Builder?) {
        if (mFlashSupported) {
            requestBuilder!!.set(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
            )
        }
    }

    override fun onSelection(path: String) {
        previewSelectedGalleryMedia(path)
    }

    override fun setSelectionButtonVisibility(size: Int) {
        setSelectedSendButtonVisibility(size)
        mTvMediacounter!!.setText(size.toString())
    }

    private class ImageSaver internal constructor(private val mImage: Image, private val mFile: File?) : Runnable {

        override fun run() {
            val buffer = mImage.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            var output: FileOutputStream? = null
            try {
                output = FileOutputStream(mFile)
                output.write(bytes)
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                mImage.close()
                if (null != output) {
                    try {
                        output.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

                }
            }
        }
    }

    companion object {

        private const val REQUEST_CAMERA_PERMISSION = 1
        private const val FRONT_CAMERA = 1
        private const val BACK_CAMERA = 0
        private const val SENSOR_ORIENTATION_DEFAULT_DEGREES = 90
        private const val SENSOR_ORIENTATION_INVERSE_DEGREES = 270
        private const val STATE_PREVIEW = 0
        private const val STATE_WAITING_LOCK = 1
        private const val STATE_WAITING_PRECAPTURE = 2
        private const val STATE_WAITING_NON_PRECAPTURE = 3
        private const val STATE_PICTURE_TAKEN = 4

        private val DEFAULT_ORIENTATIONS = SparseIntArray()
        private val INVERSE_ORIENTATIONS = SparseIntArray()
        private val ORIENTATIONS = SparseIntArray()

        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }

        init {
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90)
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0)
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270)
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }

        init {
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270)
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180)
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90)
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0)
        }

        private fun chooseVideoSize(choices: Array<Size>): Size {
            for (size in choices) {
                if (size.width == size.height * 4 / 3 && size.width <= 1080) {
                    return size
                }
            }
            return choices[choices.size - 1]
        }
    }
}