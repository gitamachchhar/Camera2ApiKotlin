package com.camera2kotlin.interfaces

import com.camera2kotlin.widgets.RangeSeekBarView

interface OnRangeSeekBarListener {
    fun onCreate(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float)

    fun onSeek(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float)

    fun onSeekStart(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float)

    fun onSeekStop(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float)
}