package com.camera2kotlin

import android.app.Application
import io.realm.Realm
import io.realm.RealmConfiguration

class BaseApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Realm.init(this)
        realmConfiguration = realmConfiguration()
        Realm.setDefaultConfiguration(realmConfiguration)
    }

    companion object {

        private var realmConfiguration: RealmConfiguration? = null

        private fun realmConfiguration(): RealmConfiguration? {
            if (realmConfiguration != null) return realmConfiguration

            realmConfiguration = RealmConfiguration.Builder()
                .name("CameraExample.db")
                .deleteRealmIfMigrationNeeded()
                .schemaVersion(1)
                .build()
            return realmConfiguration
        }
    }
}
