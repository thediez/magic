package com.timejet.bio.timejet

import android.app.Application
import android.util.Log
import com.crashlytics.android.Crashlytics
import com.timejet.bio.timejet.config.REALMBASE_NAME
import com.timejet.bio.timejet.config.REALMBASE_SCHEMA_VERSION
import com.timejet.bio.timejet.repository.RealmHandler
import com.timejet.bio.timejet.repository.StateRepository
import com.timejet.bio.timejet.ui.main.LOG_TAG
import com.timejet.bio.timejet.ui.main.appContext
import io.fabric.sdk.android.Fabric
import io.realm.Realm
import io.realm.RealmConfiguration
import kotlinx.coroutines.*

class App :  Application() {
    private val LOG_TAG = "App"
    lateinit var stateRepository: StateRepository
    override fun onCreate() {
        super.onCreate()

        appContext = this
        System.setProperty("java.net.preferIPv4Stack", "true")
        System.setProperty("org.apache.poi.javax.xml.stream.XMLInputFactory", "com.fasterxml.aalto.stax.InputFactoryImpl")
        System.setProperty("org.apache.poi.javax.xml.stream.XMLOutputFactory", "com.fasterxml.aalto.stax.OutputFactoryImpl")
        System.setProperty("org.apache.poi.javax.xml.stream.XMLEventFactory", "com.fasterxml.aalto.stax.EventFactoryImpl")

        Fabric.with(this, Crashlytics())


        RealmHandler.ioScope.launch {
            Log.d(LOG_TAG, "Scope: ${Thread.currentThread().name}, id: ${Thread.currentThread().id}")
            Realm.init(appContext)
            val realmConfiguration = RealmConfiguration.Builder()
                .name(REALMBASE_NAME)
                .name("Test")
                .schemaVersion(REALMBASE_SCHEMA_VERSION.toLong())
                .deleteRealmIfMigrationNeeded()
                .build()

            Realm.setDefaultConfiguration(realmConfiguration)
            RealmHandler.getInstance()
        }

        stateRepository = StateRepository.create(applicationContext)
    }
}
