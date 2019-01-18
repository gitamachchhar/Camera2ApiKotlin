package com.camera2kotlin.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.camera2kotlin.R
import com.camera2kotlin.activities.CameraGalleryActivity
import com.camera2kotlin.utils.MediaFileUtils
import java.util.ArrayList

class GalleryAdapter(
    private val mFileList: ArrayList<String>?,
    private val context: Context,
    private val isMultiSelect: Boolean
) :
    BaseAdapter() {
    private var holder: MyViewHolder? = null
    val selectedFileList: ArrayList<String>?
    private val options: RequestOptions
    private var isRemoved: Boolean = false

    init {
        options = RequestOptions()
        selectedFileList = ArrayList()
    }

    override fun getCount(): Int {
        return this.mFileList!!.size
    }

    override fun getItem(i: Int): Any {
        return mFileList!![i]
    }

    override fun getItemId(i: Int): Long {
        return i.toLong()
    }

    override fun getView(pos: Int, view: View, viewGroup: ViewGroup): View {

        var view1 = LayoutInflater.from(context).inflate(R.layout.horizontal_gallery_item, null)
        holder = MyViewHolder(view1)

        if (MediaFileUtils.isVideoFile(mFileList!![pos])) {
            holder!!.mediaTypeIcon.setImageResource(R.drawable.ic_outline_videocam_24px)
        } else {
            holder!!.mediaTypeIcon.setImageResource(0)
        }

        holder!!.mediaPreview.layoutParams = FrameLayout.LayoutParams(
            MediaFileUtils.getWidth(),
            MediaFileUtils.getWidth()
        )
        holder!!.tick.layoutParams = FrameLayout.LayoutParams(MediaFileUtils.getWidth(), MediaFileUtils.getWidth())

        if (selectedFileList != null && selectedFileList.contains(mFileList[pos])) {
            holder!!.tick.setImageResource(R.drawable.ic_outline_done_24px)
            holder!!.tick.setBackgroundColor(context.resources.getColor(R.color.selected_bg))
        } else {
            holder!!.tick.setImageResource(0)
            holder!!.tick.setBackgroundColor(0)
        }

        if (MediaFileUtils.isVideoFile(mFileList[pos])) {
            Glide.with(context).load(mFileList[pos]).apply(options.frame(0).error(R.drawable.image_placeholder_icon))
                .into(holder!!.mediaPreview)
        } else {
            Glide.with(context).load(mFileList[pos]).apply(options.error(R.drawable.image_placeholder_icon))
                .into(holder!!.mediaPreview)
        }

        view1.setOnLongClickListener {
            if (isMultiSelect) {
                try {
                    if (selectedFileList!!.contains(mFileList[pos]) && selectedFileList.size > 0) {
                        selectedFileList.remove(mFileList[pos])
                        isRemoved = true
                    } else {
                        selectedFileList.add(mFileList[pos])
                        isRemoved = false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                (context as CameraGalleryActivity).updateActionBar(selectedFileList!!.size)
                notifyDataSetChanged()
            }

            true
        }

        view1.setOnClickListener {
            if (!isMultiSelect) {
                (context as CameraGalleryActivity).previewSelectedGalleryMedia(mFileList[pos])
            } else {
                if (selectedFileList!!.contains(mFileList[pos])) {
                    selectedFileList.remove(mFileList[pos])
                    isRemoved = true
                } else {
                    isRemoved = false
                    if (selectedFileList.size == 10) {
                        Toast.makeText(context, "Can't share more than 10 media files", Toast.LENGTH_LONG).show()
                    } else {
                        selectedFileList.add(mFileList[pos])
                    }
                }

                if (selectedFileList.size <= 1) {
                    if (isRemoved) {
                        (context as CameraGalleryActivity).updateActionBar(selectedFileList.size)
                    } else {
                        (context as CameraGalleryActivity).previewSelectedGalleryMedia(mFileList[pos])
                    }
                } else if (selectedFileList.size > 1) {
                    (context as CameraGalleryActivity).updateActionBar(selectedFileList.size)
                } else {
                    (context as CameraGalleryActivity).previewSelectedGalleryMedia(mFileList[pos])
                }
            }


            notifyDataSetChanged()
        }

        return view1
    }

    fun clearFileList() {
        selectedFileList!!.clear()
        notifyDataSetChanged()
    }

    internal inner class MyViewHolder(view: View) {

        val mediaPreview: AppCompatImageView
        val mediaTypeIcon: AppCompatImageView
        val tick: AppCompatImageView

        init {
            mediaPreview = view.findViewById(R.id.imagePreview)
            mediaTypeIcon = view.findViewById(R.id.mediaType)
            tick = view.findViewById(R.id.tick)
        }

    }

}