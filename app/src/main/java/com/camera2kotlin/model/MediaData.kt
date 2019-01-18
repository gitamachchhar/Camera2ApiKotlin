package com.camera2kotlin.model

import java.util.UUID

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class MediaData(@PrimaryKey open var id: String = UUID.randomUUID().toString(),
                            open var filePath: String,
                            open var outPutFilePath: String,
                            open var fileType: Int = 0,
                            open var fileCaption: String,
                            open var startTime: Int = 0,
                            open var endTime: Int = 0,
                            open var mDuration: Int = 0,
                            open var mThumb1Pos: Float = 0.toFloat(),
                            open var mThumb2Pos: Float = 0.toFloat(),
                            open var isTrimmed: Boolean

                            ) : RealmObject(){}



