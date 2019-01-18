package com.camera2kotlin.activities

import android.app.ProgressDialog
import androidx.appcompat.app.AppCompatActivity
import com.camera2kotlin.R


open class ProgressActivity : AppCompatActivity() {

    private var progressDialog: ProgressDialog? = null

    fun loadProgressBar(cancellable: Boolean) {
        if (progressDialog == null && !isFinishing) {
            progressDialog = ProgressDialog(this, R.style.MyTheme)
            progressDialog!!.setCancelable(cancellable)
            progressDialog!!.setProgressStyle(android.R.style.Widget_ProgressBar_Small)
            progressDialog!!.show()
        }
    }

    fun dismissProgressBar() {
        if (progressDialog != null && progressDialog!!.isShowing) {
            progressDialog!!.dismiss()
        }
        progressDialog = null
    }

}