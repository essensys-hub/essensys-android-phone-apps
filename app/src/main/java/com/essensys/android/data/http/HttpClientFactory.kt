package com.essensys.android.data.http

import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.data.session.SessionStore
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

val EssensysJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/**
 * Ajoute l'authentification selon le mode (Bearer en cloud, cookie `essensys_lan_session` en LAN)
 * et l'en-tête dry-run quand le mode test est actif (spec domotic-controls « Mode test »).
 */
class SessionInterceptor(private val store: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val state = store.state.value
        val builder = chain.request().newBuilder()
        when (state.mode) {
            ConnectionMode.CLOUD -> state.token?.let { builder.header("Authorization", "Bearer $it") }
            ConnectionMode.LAN -> state.lanCookie?.let { builder.header("Cookie", "$LAN_COOKIE=$it") }
        }
        if (state.testMode) builder.header(NoArmoireInterceptor.TEST_MODE_HEADER, NoArmoireInterceptor.DRY_RUN)
        return chain.proceed(builder.build())
    }

    companion object {
        const val LAN_COOKIE = "essensys_lan_session"
    }
}

object HttpClientFactory {
    /**
     * @param guardNoArmoire installe la garde no-armoire (tests, et builds debug si souhaité).
     * @param configure point d'extension (TLS épinglé en LAN, design D4).
     */
    fun create(
        store: SessionStore,
        guardNoArmoire: Boolean,
        configure: OkHttpClient.Builder.() -> Unit = {},
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(SessionInterceptor(store))
        .apply { if (guardNoArmoire) addInterceptor(NoArmoireInterceptor()) }
        .apply(configure)
        .build()
}
