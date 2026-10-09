package com.essensys.android

import android.app.Application
import com.essensys.android.data.session.PersistentSessionStore
import com.essensys.android.di.AppContainer
import kotlinx.coroutines.runBlocking

class EssensysApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // v1.0.0 stockait les identifiants en clair : purge au premier lancement (spec mobile-auth).
        PersistentSessionStore.purgeLegacy(this)
        // Lecture unique et courte du DataStore : la session doit être connue avant le premier écran.
        val store = runBlocking { PersistentSessionStore.open(this@EssensysApp) }
        container = AppContainer(store, guardNoArmoire = false)
    }
}
