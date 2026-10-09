package com.essensys.android.di

import com.essensys.android.data.auth.AuthRepository
import com.essensys.android.data.control.ControlRepository
import com.essensys.android.data.http.ApiCaller
import com.essensys.android.data.http.HttpClientFactory
import com.essensys.android.data.http.LanTrust
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.data.session.PortalRepository
import com.essensys.android.data.session.SessionStore
import okhttp3.OkHttpClient

/**
 * Injection manuelle (design D1). Deux clients HTTP : cloud (magasin système) et LAN (certificat
 * de la gateway épinglé, design D4). Les tests fournissent un store mémoire et un serveur simulé.
 */
class AppContainer(
    val sessionStore: SessionStore,
    guardNoArmoire: Boolean,
    configureCloud: OkHttpClient.Builder.() -> Unit = {},
    configureLan: OkHttpClient.Builder.() -> Unit = LanTrust.configureLanClient(sessionStore),
) {
    val cloudClient: OkHttpClient = HttpClientFactory.create(sessionStore, guardNoArmoire, configureCloud)
    val lanClient: OkHttpClient = HttpClientFactory.create(sessionStore, guardNoArmoire, configureLan)
    private val api = ApiCaller {
        if (sessionStore.state.value.mode == ConnectionMode.CLOUD) cloudClient else lanClient
    }
    val auth = AuthRepository(api, sessionStore)
    val control = ControlRepository(api, sessionStore, auth)
    val portal = PortalRepository(api, sessionStore, auth)
}
