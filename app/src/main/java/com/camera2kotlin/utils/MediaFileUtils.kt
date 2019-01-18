package com.camera2kotlin.utils

import android.annotation.TargetApi
import android.content.ContentValues.TAG
import android.content.Context
import android.content.res.Resources
import android.media.*
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import com.camera2kotlin.model.MediaData
import io.realm.Realm
import java.io.File
import java.io.IOException
import java.net.URLConnection
import java.nio.ByteBuffer
import java.util.*
import java.util.regex.PatternSyntaxException

class MediaFileUtils {

    companion object {

        private val filePathList = ArrayList<String>()

        fun getMediaList(): List<MediaData> {
            val realmObject = Realm.getDefaultInstance().where(MediaData::class.java).findAll()
            return if (realmObject != null)
                Realm.getDefaultInstance().copyFromRealm(realmObject)
            else
                ArrayList()
        }

        @Throws(PatternSyntaxException::class)
        fun getFile(dir: File): ArrayList<String>? {

            val listFile = dir.listFiles()

            if (listFile != null && listFile.isNotEmpty()) {

                for (lFile in listFile) {

                    if (lFile.name.equals(
                            "Android",
                            ignoreCase = true
                        ) && lFile.isDirectory || lFile.name.startsWith(".")
                    ) {
                        continue
                    }

                    if (lFile.isDirectory) {
                        getFile(lFile)
                    } else {

                        if (lFile.name.contains(".jpg")
                            || lFile.name.contains(".jpeg")
                            || lFile.name.contains(".gif")
                            || lFile.name.contains(".png")
                            || lFile.name.contains(".mp4")
                            || lFile.name.contains(".mpeg4")
                            || lFile.name.contains(".avi")
                            || lFile.name.contains(".wmv")
                            || lFile.name.contains(".3gp")
                        ) {

                            if (lFile.name.toLowerCase().contains(".3gp") && !isVideoFile(lFile.name))
                                continue
                            else if (lFile.name.toLowerCase().contains(".wav") && !isVideoFile(lFile.name))
                                continue

                            filePathList.add(lFile.absolutePath)
                        }
                    }
                }
            }

            return if (filePathList.size == 0) null else filePathList

        }

        fun clearFileList() {
            filePathList.clear()
        }

        @Throws(PatternSyntaxException::class)
        fun getImageFile(dir: File): ArrayList<String>? {

            val listFile = dir.listFiles()

            if (listFile != null && listFile.isNotEmpty()) {

                for (lFile in listFile) {

                    if (lFile.name.equals(
                            "Android",
                            ignoreCase = true
                        ) && lFile.isDirectory || lFile.name.startsWith(".")
                    ) {
                        continue
                    }

                    if (lFile.isDirectory) {
                        getImageFile(lFile)

                    } else {

                        if (lFile.name.contains(".jpg")
                            || lFile.name.contains(".jpeg")
                            || lFile.name.contains(".png")
                        ) {
                            filePathList.add(lFile.absolutePath)
                        }
                    }
                }
            }

            return if (filePathList.size == 0) null else filePathList

        }

        fun isVideoFile(path: String): Boolean {
            val mimeType = URLConnection.guessContentTypeFromName(path)
            return mimeType != null && mimeType.startsWith("video")
        }

        fun isGifFile(path: String): Boolean {
            val mimeType = URLConnection.guessContentTypeFromName(path)
            return mimeType != null && mimeType.contains("gif")
        }

        fun convertDpToPixel(dp: Float, context: Context): Int {
            val resources = context.resources
            val metrics = resources.displayMetrics
            return (dp * (metrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)).toInt()
        }

        fun formatTimer(i: Int): String {

            var strTimer = ""

            val hour = i / 3600
            val minute = i % 3600 / 60
            val seconds = i % 60

            if (hour >= 1) {
                strTimer = String.format("%02d:%02d:%02d", hour, minute, seconds)
            } else {
                strTimer = String.format("%02d:%02d", minute, seconds)
            }

            return strTimer

        }

        fun getWidth(): Int {
            val width = Resources.getSystem().displayMetrics.widthPixels
            return width / 3

        }


        fun stringForTime(timeMs: Int): String {
            val totalSeconds = timeMs / 1000

            val seconds = totalSeconds % 60
            val minutes = totalSeconds / 60 % 60
            val hours = totalSeconds / 3600

            val mFormatter = Formatter()
            return if (hours > 0) {
                mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString()
            } else {
                mFormatter.format("%02d:%02d", minutes, seconds).toString()
            }
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Throws(IOException::class)
        fun genVideoUsingMuxer(
            srcPath: String, dstPath: String,
            startMs: Int, endMs: Int, useAudio: Boolean, useVideo: Boolean
        ) {
            // Set up MediaExtractor to read from the source.
            val extractor = MediaExtractor()
            extractor.setDataSource(srcPath)
            val trackCount = extractor.trackCount
            // Set up MediaMuxer for the destination.
            val muxer: MediaMuxer
            muxer = MediaMuxer(dstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            // Set up the tracks and retrieve the max buffer size for selected
            // tracks.
            val indexMap = HashMap<Int, Int>(trackCount)
            var bufferSize = -1
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                var selectCurrentTrack = false

                if (mime.startsWith("audio/") && useAudio) {
                    selectCurrentTrack = true
                } else if (mime.startsWith("video/") && useVideo) {
                    selectCurrentTrack = true
                }

                if (selectCurrentTrack) {
                    extractor.selectTrack(i)
                    val dstIndex = muxer.addTrack(format)
                    indexMap[i] = dstIndex
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        val newSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                        bufferSize = if (newSize > bufferSize) newSize else bufferSize
                    }
                }
            }

            if (bufferSize < 0) {
                bufferSize = 64
            }
            // Set up the orientation and starting time for extractor.
            val retrieverSrc = MediaMetadataRetriever()
            retrieverSrc.setDataSource(srcPath)
            val degreesString = retrieverSrc.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
            )
            if (degreesString != null) {
                val degrees = Integer.parseInt(degreesString)
                if (degrees >= 0) {
                    muxer.setOrientationHint(degrees)
                }
            }

            if (startMs > 0) {
                extractor.seekTo((startMs * 1000).toLong(), MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }
            // Copy the samples from MediaExtractor to MediaMuxer. We will loop
            // for copying each sample and stop when we get to the end of the source
            // file or exceed the end time of the trimming.

            val offset = 0
            var trackIndex = -1
            val dstBuf = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            try {
                muxer.start()
                while (true) {
                    bufferInfo.offset = offset
                    bufferInfo.size = extractor.readSampleData(dstBuf, offset)
                    if (bufferInfo.size < 0) {
                        Log.d(TAG, "Saw input EOS.")
                        bufferInfo.size = 0
                        break
                    } else {
                        bufferInfo.presentationTimeUs = extractor.sampleTime
                        if (endMs > 0 && bufferInfo.presentationTimeUs > endMs * 1000) {
                            Log.d(TAG, "The current sample is over the trim end time.")
                            break
                        } else {
                            bufferInfo.flags = extractor.sampleFlags
                            trackIndex = extractor.sampleTrackIndex
                            muxer.writeSampleData(
                                indexMap[trackIndex]!!, dstBuf,
                                bufferInfo
                            )
                            extractor.advance()
                        }
                    }
                }
                muxer.stop()

                //deleting the old file
                /*File file = new File(srcPath);
                file.delete();*/
            } catch (e: IllegalStateException) {
                // Swallow the exception due to malformed source.
                Log.w(TAG, "The source video file is malformed")
            } finally {
                muxer.release()
            }
            return
        }
    }


}
