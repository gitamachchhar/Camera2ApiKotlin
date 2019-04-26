package com.camera2kotlin.utils

import android.app.Application
import io.realm.Realm
import io.realm.RealmConfiguration

class BaseApp : Application() {

    private var realmConfiguration: RealmConfiguration? = null

    override fun onCreate() {
        super.onCreate()
        Realm.init(this)
        realmConfiguration = realmConfiguration()
        Realm.setDefaultConfiguration(realmConfiguration!!)
    }

    private fun realmConfiguration(): RealmConfiguration? {
        if (realmConfiguration != null) return realmConfiguration

       return RealmConfiguration.Builder()
            .name("CameraExample.db")
            .deleteRealmIfMigrationNeeded()
            .schemaVersion(1)
            .build()

    }

}
