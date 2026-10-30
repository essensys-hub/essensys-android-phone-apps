package com.essensys.android.di

import com.essensys.android.data.auth.AuthRepository
import com.essensys.android.data.control.ComfortRepository
import com.essensys.android.data.control.ControlRepository
import com.essensys.android.data.control.HeatingRepository
import com.essensys.android.data.control.ScenarioRepository
import com.essensys.android.data.http.ApiCaller
import com.essensys.android.data.http.HttpClientFactory
import com.essensys.android.data.http.LanTrust
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.data.session.PortalRepository
import com.essensys.android.data.session.SessionStore
import com.essensys.android.data.support.AppLog
import com.essensys.android.data.support.DeviceProbe
import com.essensys.android.data.support.StaticDeviceProbe
import com.essensys.android.data.support.SupportBundle
import com.essensys.android.data.support.SupportDiagnostics
import com.essensys.android.data.support.SupportMail
import com.essensys.android.data.support.SupportPackage
import android.content.Context
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Injection manuelle (design D1). Deux clients HTTP : cloud (magasin système) et LAN (certificat
 * de la gateway épinglé, design D4). Les tests fournissent un store mémoire et un serveur simulé.
 */
class AppContainer(
    val sessionStore: SessionStore,
    guardNoArmoire: Boolean,
    configureCloud: OkHttpClient.Builder.() -> Unit = {},
    configureLan: OkHttpClient.Builder.() -> Unit = LanTrust.configureLanClient(sessionStore),
    /** Journal du bouton Support ; en mémoire par défaut (tests). */
    val appLog: AppLog = AppLog(),
    device: DeviceProbe = StaticDeviceProbe(),
    supportDir: File = File(System.getProperty("java.io.tmpdir"), "essensys-support"),
    /** Ouvre l'appli email ; remplacé par les tests pour vérifier le message sans quitter l'app. */
    val mailer: (Context, SupportPackage) -> Unit = SupportMail::send,
) {
    val cloudClient: OkHttpClient = HttpClientFactory.create(sessionStore, guardNoArmoire, configureCloud, appLog)
    val lanClient: OkHttpClient = HttpClientFactory.create(sessionStore, guardNoArmoire, configureLan, appLog)
    private val api = ApiCaller {
        if (sessionStore.state.value.mode == ConnectionMode.CLOUD) cloudClient else lanClient
    }
    val auth = AuthRepository(api, sessionStore)
    val control = ControlRepository(api, sessionStore, auth)
    val portal = PortalRepository(api, sessionStore, auth)
    val scenarios = ScenarioRepository(api, sessionStore, auth)
    val comfort = ComfortRepository(api, sessionStore, auth, control)
    var heating = HeatingRepository(api, sessionStore, auth, control)
        private set

    // Diagnostics : clients sans journal (les tests ne doivent pas le remplir) et à délai court.
    val supportDiagnostics = run {
        fun client(configure: OkHttpClient.Builder.() -> Unit) =
            HttpClientFactory.create(sessionStore, guardNoArmoire, configure = { configure(); callTimeout(4, TimeUnit.SECONDS) })
        SupportDiagnostics(sessionStore, client(configureCloud), client(configureLan), device)
    }
    val supportBundle = SupportBundle(supportDir, sessionStore, appLog)

    /** Tests : périodes de synchronisation courtes (temps réel, pas de temps virtuel avec OkHttp). */
    fun withHeatingSyncTiming(pollMs: Long, timeoutMs: Long) = apply {
        heating = HeatingRepository(api, sessionStore, auth, control, pollMs, timeoutMs)
    }
}
