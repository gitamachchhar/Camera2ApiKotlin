package com.camera2kotlin.activities

import android.media.MediaPlayer
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.Nullable
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.camera2kotlin.R
import com.camera2kotlin.adapters.HorizontalMediaGalleryAdapter
import com.camera2kotlin.interfaces.MediaSelectionCallback
import com.camera2kotlin.interfaces.OnTrimVideoListener
import com.camera2kotlin.interfaces.OnVideoListener
import com.camera2kotlin.model.MediaData
import com.camera2kotlin.utils.Constants.ISIMAGEONLY
import com.camera2kotlin.utils.MediaFileUtils
import com.camera2kotlin.widgets.VideoTrimmer
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.realm.Realm
import java.io.IOException
import java.util.*

class SelectMediaFileActivity : ProgressActivity(), View.OnClickListener, MediaSelectionCallback, OnTrimVideoListener,
    OnVideoListener {

    private var fullImageView: AppCompatImageView? = null
    private var edtCaption: AppCompatEditText? = null
    private var selectedListMedia: RecyclerView? = null
    private var rlVideoView: LinearLayout? = null
    private var llContainer: LinearLayout? = null

    private var mediaType = MEDIA_PICTURE
    private var trimCounter = 0
    private var isPlaying = false
    private var isTrimRequired: Boolean = false
    private var isTextChanged: Boolean = false
    private var mMediaPath: String? = null
    private var mediaData: MediaData? = null
    private var mVideoTrimmer: VideoTrimmer? = null
    private val trimFileList = ArrayList<String>()

    override fun onCreate(@Nullable savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.topsheet)
        initViews()
        setData()
    }

    private fun initViews() {

        val isImageOnly = intent.getBooleanExtra(ISIMAGEONLY, false)

        val sendMessage = findViewById<FloatingActionButton>(R.id.sendMessage)
        val iv_imageBack = findViewById<AppCompatImageView>(R.id.iv_imageBack)
        val icon_back_camera = findViewById<AppCompatImageView>(R.id.icon_back_camera)

        llContainer = findViewById(R.id.llContainer)
        edtCaption = findViewById(R.id.edtCaption)
        fullImageView = findViewById(R.id.fullImageView)
        rlVideoView = findViewById(R.id.rlVideoView)
        val bottomCaptionLayout = findViewById<LinearLayout>(R.id.bottomCaptionLayout)

        val manager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        selectedListMedia = findViewById(R.id.selectedListMedia)
        selectedListMedia!!.layoutManager = manager

        icon_back_camera.setOnClickListener(this)
        sendMessage.setOnClickListener(this)
        iv_imageBack.setOnClickListener(this)

        if (isImageOnly) {
            bottomCaptionLayout.visibility = View.INVISIBLE
        }

        edtCaption!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {

            }

            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {

            }

            override fun afterTextChanged(editable: Editable) {
                isTextChanged = !edtCaption!!.getText().toString().isEmpty()
            }
        })
    }

    private fun setData() {

        val mode = intent.getStringExtra("mode")
        mediaData = Realm.getDefaultInstance().where(MediaData::class.java).findFirst()

        if (mediaData != null) {
            mMediaPath = mediaData!!.filePath
            mediaType = mediaData!!.fileType
        }

        if (mode.equals("SingleSelect", ignoreCase = true)) {

            if (mediaType == MEDIA_VIDEO) {
                setVideoSettings()
            } else {
                setSelectedMediaInView(MEDIA_PICTURE)
            }

        } else if (mode.equals("MultiSelect", ignoreCase = true)) {

            val mediaDataArrayList = Realm.getDefaultInstance().where(MediaData::class.java).findAll()
            setSelectedMediaInView(mediaType)

            if (mediaType == MEDIA_VIDEO)
                setVideoSettings()

            val pathList = ArrayList<String>()

            for (mData in mediaDataArrayList) {
                pathList.add(mData.filePath)
            }

            val mListAdapter = HorizontalMediaGalleryAdapter(pathList, this, this, true)
            selectedListMedia!!.adapter = mListAdapter
        }
    }

    private fun setVideoSettings() {

        fullImageView!!.visibility = View.GONE
        llContainer!!.visibility = View.VISIBLE
        val mp = MediaPlayer.create(this, Uri.parse(mMediaPath))
        val duration = mp.duration
        mp.release()

        llContainer!!.removeAllViews()
        mVideoTrimmer = VideoTrimmer(this)
        mVideoTrimmer!!.setMaxDuration(duration * 1000)
        mVideoTrimmer!!.setOnTrimVideoListener(this)
        mVideoTrimmer!!.setOnVideoListenre(this)
        mVideoTrimmer!!.setMediaData(mediaData!!)
        mVideoTrimmer!!.setVideoURI(Uri.parse(mMediaPath))
        mVideoTrimmer!!.setVideoInformationVisibility(true)
        llContainer!!.addView(mVideoTrimmer)

    }

    private fun resetVideoView() {
        llContainer!!.visibility = View.GONE
        rlVideoView!!.visibility = View.GONE
        fullImageView!!.visibility = View.VISIBLE
        isPlaying = false
    }

    override fun onVideoPrepared() {

    }

    override fun onTrimStarted() {

    }

    override fun getResult(uri: Uri) {

    }

    override fun cancelAction() {

    }

    override fun onError(message: String) {

        runOnUiThread { Toast.makeText(this@SelectMediaFileActivity, message, Toast.LENGTH_SHORT).show() }
    }

    override fun onClick(view: View) {

        when (view.id) {

            R.id.iv_imageBack -> finish()

            R.id.sendMessage ->

                if (mVideoTrimmer != null && mVideoTrimmer!!.isUpdateRequired || isTextChanged) {

                    if (isTextChanged) {

                        val data = Realm.getDefaultInstance().copyFromRealm(mediaData)
                        data?.fileCaption = edtCaption!!.text.toString()

                        Realm.getDefaultInstance()
                            .executeTransactionAsync(Realm.Transaction { realm -> realm.insertOrUpdate(data) },
                                Realm.Transaction.OnSuccess { isTextChanged = false })
                    }

                    if (mVideoTrimmer != null && mVideoTrimmer!!.isUpdateRequired) {
                        mVideoTrimmer!!.insertMediaInTable()
                    }

                    Handler().postDelayed({ submitData() }, 100)

                } else {
                    submitData()
                }

            R.id.icon_play -> {
                rlVideoView!!.visibility = View.VISIBLE
                fullImageView!!.visibility = View.GONE
                isPlaying = !isPlaying
            }

            R.id.icon_back_camera -> finish()
        }
    }

    private fun submitData() {

        val mediaDataList = Realm.getDefaultInstance().where(MediaData::class.java).findAll()

        for (d in mediaDataList) {
            if (d.fileType == MEDIA_VIDEO && d.isTrimmed) {
                trimFileList.add(d.filePath)
                isTrimRequired = true
            }
        }

        if (!isTrimRequired) {
            setResult(102)
            finish()
        } else {
            VideoTrimmingTask().execute()
        }
    }

    private fun setSelectedMediaInView(type: Int) {

        fullImageView!!.visibility = View.VISIBLE

        runOnUiThread {
            if (type == MEDIA_PICTURE) {
                Glide.with(this@SelectMediaFileActivity)
                    .load(mMediaPath)
                    .apply(RequestOptions().skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.NONE))
                    .into(fullImageView!!)
            }
        }
    }

    fun previewSelectedGalleryMedia(path: String) {

        try {

            val data = Realm.getDefaultInstance().copyFromRealm(mediaData)
            if (data != null) {
                data.fileCaption = edtCaption!!.text.toString()
            }
            isTextChanged = false

            Realm.getDefaultInstance().executeTransactionAsync({ realm -> realm.insertOrUpdate(data) }, {
                edtCaption!!.setText("")
                viewImage(path)
            }, { })

        } catch (e: Exception) {
            Log.e("System out", "my issue..." + e.message)
        }

    }

    private fun viewImage(path: String) {

        mMediaPath = path
        mediaData = Realm.getDefaultInstance().where(MediaData::class.java).equalTo("filePath", path)
            .findFirst()

        if (MediaFileUtils.isVideoFile(path)) {
            mediaType = MEDIA_VIDEO
            setVideoSettings()
        } else {
            mediaType = MEDIA_PICTURE
            resetVideoView()
            setSelectedMediaInView(mediaType)
        }
        edtCaption!!.setText(mediaData!!.fileCaption)
    }

    override fun onSelection(path: String) {
        previewSelectedGalleryMedia(path)
    }

    override fun setSelectionButtonVisibility(size: Int) {

    }

    private inner class VideoTrimmingTask : AsyncTask<String, Float, Boolean>() {

        override fun onPreExecute() {
            super.onPreExecute()
            if (trimCounter == 0)
                loadProgressBar(true)
        }

        override fun doInBackground(vararg paths: String): Boolean? {
            try {
                val d = Realm.getDefaultInstance().where(MediaData::class.java!!)
                    .equalTo("filePath", trimFileList[trimCounter]).findFirst()
                if (d != null)
                    MediaFileUtils.genVideoUsingMuxer(
                        d.filePath,
                        d.outPutFilePath,
                        d.startTime,
                        d.endTime,
                        true,
                        true
                    )

            } catch (e: IOException) {
                e.printStackTrace()
            }

            return true
        }

        override fun onPostExecute(result: Boolean?) {
            super.onPostExecute(result)
            trimCounter++
            if (trimCounter < trimFileList.size) {
                VideoTrimmingTask().execute()
            } else {
                dismissProgressBar()
                setResult(102)
                finish()
            }
        }
    }

    companion object {

        private val MEDIA_VIDEO = 1
        private val MEDIA_PICTURE = 0
    }

}


