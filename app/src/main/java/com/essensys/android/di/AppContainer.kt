package com.essensys.android.di

import com.essensys.android.data.auth.AuthRepository
import com.essensys.android.data.control.ControlRepository
import com.essensys.android.data.http.ApiCaller
import com.essensys.android.data.http.HttpClientFactory
import com.essensys.android.data.session.PortalRepository
import com.essensys.android.data.session.SessionStore
import okhttp3.OkHttpClient

/** Injection manuelle (design D1). Les tests fournissent un store mémoire et un serveur simulé. */
class AppContainer(
    val sessionStore: SessionStore,
    guardNoArmoire: Boolean,
    configureHttp: OkHttpClient.Builder.() -> Unit = {},
) {
    val httpClient: OkHttpClient = HttpClientFactory.create(sessionStore, guardNoArmoire, configureHttp)
    private val api = ApiCaller(httpClient)
    val auth = AuthRepository(api, sessionStore)
    val control = ControlRepository(api, sessionStore, auth)
    val portal = PortalRepository(api, sessionStore, auth)
}
