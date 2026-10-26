package com.essensys.android.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.data.session.SessionState
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Armoire « en ligne » avec ou sans gateway (armoire-online-sans-gateway-2026-10-012), contre le backend simulé.
 * Cas A à G de la matrice du ticket essensys-hub/essensys-feature-lifecycle#37.
 */
@RunWith(AndroidJUnit4::class)
class ArmoireOnlineFlowTest {

    @get:Rule val compose = createComposeRule()
    private val backend = FakeBackend()

    @After fun tearDown() = backend.close()

    private fun exists(tag: String) = runCatching { compose.onNodeWithTag(tag).assertExists() }.isSuccess

    private fun expectOnline(online: Boolean) {
        compose.setContent { EssensysRoot(backend.container(SessionState(token = "jwt-test"))) }
        compose.waitUntil(5_000) { exists(if (online) "status-online" else "status-offline") }
        if (online) compose.onNodeWithTag("quick-2").assertIsEnabled() else compose.onNodeWithTag("quick-2").assertIsNotEnabled()
    }

    @Test
    fun avec_gateway_en_ligne() = expectOnline(true)

    @Test
    fun gateway_hors_ligne_armoire_vue() {
        backend.gatewayOnline = false
        backend.armoireOnline = true
        expectOnline(true)
    }

    @Test
    fun gateway_et_armoire_hors_ligne() {
        backend.gatewayOnline = false
        expectOnline(false)
    }

    // NR: NR-android-19 essensys-hub/essensys-android-phone-apps#25
    // Signalement du 2026-10-26 (essensys-feature-lifecycle#37) : installation sans gateway, armoire joignable.
    @Test
    fun sans_gateway_armoire_vue_en_ligne_NR_android_19() {
        backend.hasGateway = false
        backend.armoireOnline = true
        expectOnline(true)
    }

    @Test
    fun sans_gateway_armoire_ancienne_hors_ligne() {
        backend.hasGateway = false
        backend.armoireOnline = false
        expectOnline(false)
    }

    @Test
    fun sans_gateway_ni_armoire_hors_ligne() {
        backend.hasGateway = false
        backend.hasArmoire = false
        expectOnline(false)
    }

    @Test
    fun mode_LAN_commandes_actives() {
        compose.setContent { EssensysRoot(backend.container(SessionState(mode = ConnectionMode.LAN, lanCookie = "abc"))) }
        compose.waitUntil(5_000) { exists("quick-2") }
        compose.onNodeWithTag("quick-2").assertIsEnabled()
    }
}
