package com.essensys.android.support

import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.data.session.InMemorySessionStore
import com.essensys.android.data.session.SessionState
import com.essensys.android.di.AppContainer
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

/** Serveur simulé + conteneur câblé dessus, garde no-armoire active (spec android-quality). */
class TestServer(mode: ConnectionMode = ConnectionMode.CLOUD, token: String? = null, testMode: Boolean = false) : AutoCloseable {
    val server = MockWebServer().apply { start() }
    private val base = server.url("/").toString().trimEnd('/')
    val store = InMemorySessionStore(
        SessionState(mode = mode, cloudHost = base, lanHost = base, token = token, testMode = testMode),
    )
    val container = AppContainer(store, guardNoArmoire = true)

    fun enqueue(code: Int, body: String = "", vararg headers: Pair<String, String>) {
        val builder = MockResponse.Builder().code(code).body(body)
        headers.forEach { (k, v) -> builder.addHeader(k, v) }
        server.enqueue(builder.build())
    }

    fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/$name")) { "fixture $name" }.readText()

    override fun close() = server.close()
}
