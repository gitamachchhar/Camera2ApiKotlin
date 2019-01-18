package com.camera2kotlin.activities

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.camera2kotlin.R
import com.camera2kotlin.utils.Constants.ISGALLERY
import com.camera2kotlin.utils.Constants.ISIMAGEONLY
import com.camera2kotlin.utils.Constants.ISMULTISELECT
import com.camera2kotlin.utils.Constants.MEDIA_VIDEO
import com.camera2kotlin.utils.Constants.REQUEST_IMAGE
import com.camera2kotlin.utils.MediaFileUtils
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import java.io.File


class MainActivity : AppCompatActivity() {

    private var tvClick: TextView? = null

    private val permissionListener = object : PermissionListener {
        override fun onPermissionGranted() {
            val intent = Intent(this@MainActivity, CameraGalleryActivity::class.java)
            intent.putExtra(ISGALLERY, false)
            intent.putExtra(ISIMAGEONLY, false)
            intent.putExtra(ISMULTISELECT, true)
            startActivityForResult(intent, REQUEST_IMAGE)
        }

        override fun onPermissionDenied(deniedPermissions: List<String>) {
            Toast.makeText(
                this@MainActivity,
                getResources().getString(R.string.permission_denial_msg),
                Toast.LENGTH_LONG
            ).show()
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvClick = findViewById(R.id.ClickMe)
        tvClick?.setOnClickListener { askPermissionforCamera() }
    }

    private fun askPermissionforCamera() {
        TedPermission.with(this)
            .setPermissionListener(permissionListener)
            .setDeniedMessage(getString(R.string.permission_denial_msg))
            .setPermissions(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
            .check()
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        for (mediaData in MediaFileUtils.getMediaList()) {

            if (mediaData.fileType == MEDIA_VIDEO) {
                var filePath = mediaData.outPutFilePath
                if (!mediaData.isTrimmed)
                    filePath = mediaData.filePath
                val source = File(filePath)

            } else {
                var filePath = mediaData.outPutFilePath
                if (!mediaData.isTrimmed)
                    filePath = mediaData.filePath
                val source = File(filePath)

            }

        }
    }


}
