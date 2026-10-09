package com.essensys.android.data.http

import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.data.session.InMemorySessionStore
import com.essensys.android.data.session.SessionState
import com.essensys.android.di.AppContainer
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Épinglage TOFU du certificat de la gateway LAN (spec connection-modes « HTTPS uniquement », design D4). */
class LanTrustTest {

    private val gatewayCa = HeldCertificate.Builder().certificateAuthority(0).commonName("Essensys gateway CA").build()
    private val serverCert = HeldCertificate.Builder()
        .addSubjectAlternativeName("localhost")
        .signedBy(gatewayCa)
        .build()
    private val server = MockWebServer().apply {
        useHttps(HandshakeCertificates.Builder().heldCertificate(serverCert, gatewayCa.certificate).build().sslSocketFactory())
        start()
    }
    private val base = server.url("/").toString().trimEnd('/').replace("127.0.0.1", "localhost")

    @After fun tearDown() = server.close()

    private fun container(pinned: String?): AppContainer {
        val store = InMemorySessionStore(SessionState(mode = ConnectionMode.LAN, lanHost = base, lanCookie = "c", pinnedLanCertPem = pinned))
        return AppContainer(store, guardNoArmoire = true)
    }

    @Test
    fun probe_returns_presented_gateway_ca_and_fingerprint() = runTest {
        server.enqueue(MockResponse.Builder().code(404).build())
        val probed = LanTrust.probe(base)
        val cert = (probed as ApiResult.Ok).value
        assertEquals(LanTrust.sha256(gatewayCa.certificate), LanTrust.sha256(cert))
        assertTrue(LanTrust.sha256(cert).matches(Regex("([0-9A-F]{2}:){31}[0-9A-F]{2}")))
    }

    @Test
    fun requests_fail_until_certificate_is_confirmed() = runTest {
        val result = container(pinned = null).portal.lanSessionValid()
        assertTrue(result is ApiResult.Err && result.error is ApiError.Network)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun pinned_gateway_certificate_is_trusted() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"user":{"id":1}}""").build())
        val result = container(pinned = LanTrust.toPem(gatewayCa.certificate)).portal.lanSessionValid()
        assertEquals(ApiResult.Ok(true), result)
    }

    @Test
    fun another_ca_is_rejected() = runTest {
        val otherCa = HeldCertificate.Builder().certificateAuthority(0).commonName("Autre CA").build()
        val result = container(pinned = LanTrust.toPem(otherCa.certificate)).portal.lanSessionValid()
        assertTrue(result is ApiResult.Err && result.error is ApiError.Network)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun pem_roundtrip_keeps_fingerprint() {
        val pem = LanTrust.toPem(gatewayCa.certificate)
        assertEquals(LanTrust.sha256(gatewayCa.certificate), LanTrust.sha256(LanTrust.fromPem(pem)))
    }
}
