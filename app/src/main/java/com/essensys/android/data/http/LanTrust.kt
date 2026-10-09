package com.essensys.android.data.http

import android.annotation.SuppressLint
import com.essensys.android.data.session.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * TLS en réseau local (design D4) : chaque gateway a sa propre CA, il n'y a pas de CA commune à embarquer.
 * Première connexion : [probe] récupère le certificat présenté (sans envoyer de donnée applicative),
 * l'utilisateur compare son empreinte SHA-256 puis confirme ; ensuite seul ce certificat est accepté
 * comme ancre, avec vérification du nom d'hôte (jamais de « trust all » pour les vraies requêtes).
 */
object LanTrust {

    fun sha256(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString(":") { "%02X".format(it) }

    fun toPem(cert: X509Certificate): String =
        "-----BEGIN CERTIFICATE-----\n" +
            Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(cert.encoded) +
            "\n-----END CERTIFICATE-----\n"

    fun fromPem(pem: String): X509Certificate =
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(pem.toByteArray())) as X509Certificate

    /**
     * Récupère l'ancre proposée par la gateway : le dernier certificat de la chaîne présentée
     * (la CA si la gateway l'envoie, sinon le certificat serveur lui-même).
     * La connexion de sondage ne transporte qu'un HEAD sur `/` et n'est jamais réutilisée.
     */
    suspend fun probe(baseUrl: String, configure: OkHttpClient.Builder.() -> Unit = {}): ApiResult<X509Certificate> =
        withContext(Dispatchers.IO) {
            val captured = CapturingTrustManager()
            val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(captured), null) }
            val client = OkHttpClient.Builder()
                .sslSocketFactory(ssl.socketFactory, captured)
                .hostnameVerifier { _, _ -> true } // sondage uniquement : on ne lit que le certificat
                .connectTimeout(5, TimeUnit.SECONDS)
                .apply(configure)
                .build()
            try {
                client.newCall(Request.Builder().url(baseUrl.trimEnd('/') + "/").head().build()).execute().close()
            } catch (_: Exception) {
                // L'échec applicatif importe peu : seul le handshake nous intéresse.
            }
            captured.chain?.lastOrNull()?.let { ApiResult.Ok(it) }
                ?: ApiResult.Err(ApiError.Network(baseUrl.substringAfter("://"), "aucun certificat présenté"))
        }

    /** TrustManager des vraies requêtes LAN : n'accepte que la chaîne ancrée sur le certificat épinglé. */
    fun pinnedTrustManager(store: SessionStore): X509TrustManager = PinnedTrustManager(store)

    fun configureLanClient(store: SessionStore): OkHttpClient.Builder.() -> Unit = {
        val trustManager = pinnedTrustManager(store)
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
        sslSocketFactory(ssl.socketFactory, trustManager)
    }

    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private class CapturingTrustManager : X509TrustManager {
        var chain: Array<X509Certificate>? = null
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            this.chain = chain?.map { it }?.toTypedArray()
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    @SuppressLint("CustomX509TrustManager")
    private class PinnedTrustManager(private val store: SessionStore) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
            throw CertificateException("client auth non supportée")

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val pem = store.state.value.pinnedLanCertPem
                ?: throw CertificateException("Certificat de la gateway non confirmé")
            delegate(fromPem(pem)).checkServerTrusted(chain, authType)
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

        private fun delegate(anchor: X509Certificate): X509TrustManager {
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null)
                setCertificateEntry("essensys-gateway", anchor)
            }
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(keyStore) }
            return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
        }
    }
}
