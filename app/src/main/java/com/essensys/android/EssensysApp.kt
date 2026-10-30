package com.essensys.android

import android.app.Application
import com.essensys.android.data.session.PersistentSessionStore
import com.essensys.android.data.support.AndroidDeviceProbe
import com.essensys.android.data.support.AppLog
import com.essensys.android.di.AppContainer
import kotlinx.coroutines.runBlocking
import java.io.File

class EssensysApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // v1.0.0 stockait les identifiants en clair : purge au premier lancement (spec mobile-auth).
        PersistentSessionStore.purgeLegacy(this)
        // Lecture unique et courte du DataStore : la session doit être connue avant le premier écran.
        val store = runBlocking { PersistentSessionStore.open(this@EssensysApp) }
        container = AppContainer(
            store,
            guardNoArmoire = false,
            appLog = AppLog(File(filesDir, "support/journal.jsonl")),
            device = AndroidDeviceProbe(this),
            supportDir = File(cacheDir, "support"),
        )
        container.appLog.event("app", "Démarrage ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    }
}
