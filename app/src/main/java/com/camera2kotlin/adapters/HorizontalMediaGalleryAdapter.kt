package com.camera2kotlin.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.camera2kotlin.R
import com.camera2kotlin.activities.CameraGalleryActivity
import com.camera2kotlin.interfaces.MediaSelectionCallback
import com.camera2kotlin.utils.MediaFileUtils
import java.util.ArrayList

class HorizontalMediaGalleryAdapter(
    private val mFileList: List<String>,
    private val mContext: Context,
    private val mediaCallback: MediaSelectionCallback,
    private val isMultiSelect: Boolean
) : RecyclerView.Adapter<HorizontalMediaGalleryAdapter.MyViewHolder>() {

    private val options: RequestOptions
    val selectedFilesList: ArrayList<String>?
    private var isRemoved: Boolean = false

    override fun getItemCount(): Int {
        return mFileList.size
    }

    init {
        options = RequestOptions()
        selectedFilesList = ArrayList()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.horizontal_gallery_item, parent, false)
        return MyViewHolder(itemView)
    }

    override fun onBindViewHolder(@NonNull holder: MyViewHolder, position: Int) {
        val pos = holder.getAdapterPosition()

        if (MediaFileUtils.isGifFile(mFileList[pos])) {
            holder.mediaTypeIcon.setImageResource(0)
        } else if (MediaFileUtils.isVideoFile(mFileList[pos])) {
            holder.mediaTypeIcon.setImageResource(R.drawable.ic_outline_videocam_24px)
        } else {
            holder.mediaTypeIcon.setImageResource(0)
        }


        if (MediaFileUtils.isVideoFile(mFileList[pos])) {
            Glide.with(mContext).load(mFileList[pos]).apply(options.frame(0).error(R.drawable.image_placeholder_icon))
                .into(holder.mediaPreview)
        } else {
            Glide.with(mContext).load(mFileList[pos]).apply(options.error(R.drawable.image_placeholder_icon))
                .into(holder.mediaPreview)
        }

        if (selectedFilesList != null && selectedFilesList.contains(mFileList[pos])) {
            holder.tick.setImageResource(R.drawable.ic_outline_done_24px)
            holder.tick.setBackgroundColor(mContext.resources.getColor(R.color.selected_bg))
        } else {
            holder.tick.setImageResource(0)
            holder.tick.setBackgroundColor(0)
        }

        holder.itemView.setOnLongClickListener {
            if (mContext is CameraGalleryActivity && isMultiSelect) {
                try {
                    if (selectedFilesList!!.contains(mFileList[pos]) && selectedFilesList.size > 0) {
                        selectedFilesList.remove(mFileList[pos])
                        isRemoved = true
                    } else {
                        selectedFilesList.add(mFileList[pos])
                        isRemoved = false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                notifyDataSetChanged()
                mediaCallback.setSelectionButtonVisibility(selectedFilesList!!.size)
            }
            true
        }



        holder.itemView.setOnClickListener {
            if (!isMultiSelect) {
                (mContext as CameraGalleryActivity).previewSelectedGalleryMedia(mFileList[pos])
            } else {

                if (mContext is CameraGalleryActivity) {
                    if (selectedFilesList!!.contains(mFileList[pos]) && selectedFilesList.size > 0) {
                        selectedFilesList.remove(mFileList[pos])
                        isRemoved = true
                    } else {
                        isRemoved = false
                        if (selectedFilesList.size == 10) {
                            Toast.makeText(mContext, "Can't share more than 10 media files", Toast.LENGTH_LONG).show()
                        } else {
                            selectedFilesList.add(mFileList[pos])
                        }
                    }

                    if (selectedFilesList.size <= 1) {
                        if (isRemoved) {
                            mediaCallback.setSelectionButtonVisibility(selectedFilesList.size)
                        } else {
                            mediaCallback.onSelection(mFileList[pos])
                        }
                    } else if (selectedFilesList.size > 1) {
                        mediaCallback.setSelectionButtonVisibility(selectedFilesList.size)
                    } else {
                        mediaCallback.onSelection(mFileList[pos])
                    }
                } else {
                    mediaCallback.onSelection(mFileList[pos])
                }
            }
            notifyDataSetChanged()
        }

    }

    fun clearFileList() {
        selectedFilesList!!.clear()
        notifyDataSetChanged()
    }

    inner class MyViewHolder(view: View) : RecyclerView.ViewHolder(view) {

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