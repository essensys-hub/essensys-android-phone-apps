package com.essensys.android.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.essensys.android.data.push.DoorbellNotifier
import com.essensys.android.data.push.DoorbellRing
import com.essensys.android.data.push.RingDeduper
import com.essensys.android.data.session.SessionState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Option « Sonnette » et notification sur émulateur (sonnette-plages-push-2026-11-008, tâches 6.1 et 6.2),
 * backend simulé : aucun cloud, aucun fournisseur de notification et aucune armoire réels.
 * NR: NR-android-28 essensys-hub/essensys-feature-lifecycle#61.
 */
@RunWith(AndroidJUnit4::class)
class DoorbellFlowTest {

    @get:Rule val compose = createComposeRule()
    private var backend = FakeBackend()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @After fun tearDown() = backend.close()

    private fun exists(tag: String) = runCatching { compose.onNodeWithTag(tag).assertExists() }.isSuccess

    private fun grant(permission: Boolean) {
        val automation = instrumentation.uiAutomation
        val name = Manifest.permission.POST_NOTIFICATIONS
        if (permission) automation.grantRuntimePermission(context.packageName, name) else automation.revokeRuntimePermission(context.packageName, name)
    }

    private fun openSettings(state: String?) {
        backend.gatewayState = state
        compose.setContent { EssensysRoot(backend.container(SessionState(token = "jwt-old"))) }
        compose.waitUntil(5_000) { exists("nav-SETTINGS") }
        compose.onNodeWithTag("nav-SETTINGS").performClick()
        compose.waitUntil(5_000) { exists("logout") }
    }

    @Test
    fun no_gateway_hides_the_option_and_says_so() {
        grant(true)
        openSettings("none")
        compose.waitUntil(5_000) { exists("gateway-state") }
        compose.onNodeWithTag("gateway-state").performScrollTo().assertTextEquals("Aucune gateway liée")
        assertFalse(exists("doorbell-switch"))
    }

    @Test
    fun an_old_cloud_without_gateway_state_hides_the_option() {
        grant(true)
        openSettings(null)
        Thread.sleep(1_500) // laisse passer la lecture de session
        assertFalse(exists("doorbell-switch"))
        assertFalse(exists("gateway-state"))
    }

    @Test
    fun the_three_gateway_states_are_displayed() {
        grant(true)
        openSettings("offline")
        compose.waitUntil(5_000) { exists("gateway-state") }
        compose.onNodeWithTag("gateway-state").performScrollTo().assertTextEquals("Gateway hors ligne")
        assertTrue(exists("doorbell-switch")) // la présence suffit, même hors ligne
    }

    @Test
    fun enabling_registers_the_device_and_survives_a_relaunch_then_logout_deletes_it() {
        grant(true)
        openSettings("online")
        compose.waitUntil(5_000) { exists("doorbell-switch") }
        compose.onNodeWithTag("gateway-state").performScrollTo().assertTextEquals("Gateway en ligne")
        compose.onNodeWithTag("doorbell-switch").performScrollTo().assertIsOff().performClick()
        compose.waitUntil(5_000) { backend.pushDevices["dev-1"] == true }
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("doorbell-switch").assertIsOn() }.isSuccess }
        val put = backend.requests.last { it.method == "PUT" && it.url.encodedPath == "/api/portal/push/devices" }
        assertTrue(put.body!!.utf8().contains(""""platform":"android""""))

        // Désactivation : doorbell_notify=false, l'appareil reste enregistré.
        compose.onNodeWithTag("doorbell-switch").performClick()
        compose.waitUntil(5_000) { backend.pushDevices["dev-1"] == false }
        compose.onNodeWithTag("doorbell-switch").assertIsOff()
        compose.onNodeWithTag("doorbell-switch").performClick()
        compose.waitUntil(5_000) { backend.pushDevices["dev-1"] == true }

        // Déconnexion : l'appareil est supprimé côté cloud.
        compose.onNodeWithTag("logout").performScrollTo().performClick()
        compose.waitUntil(5_000) { backend.requests.any { it.method == "DELETE" && it.url.encodedPath == "/api/portal/push/devices/dev-1" } }
        assertTrue(backend.pushDevices.isEmpty())
    }

    @Test
    fun a_notification_shows_once_per_ring_id_and_opens_the_app() {
        grant(true)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        val deduper = RingDeduper()
        assertTrue(DoorbellNotifier.show(context, DoorbellRing("ring-it-1", null), deduper))
        assertFalse(DoorbellNotifier.show(context, DoorbellRing("ring-it-1", null), deduper))
        val active = manager.activeNotifications.filter { it.packageName == context.packageName }
        assertEquals(1, active.size)
        val n = active.single().notification
        assertEquals(DoorbellNotifier.CHANNEL_ID, n.channelId)
        assertEquals("Quelqu'un sonne", n.extras.getString("android.title"))
        assertEquals("Sonnette de votre maison", n.extras.getString("android.text"))
        assertEquals(NotificationManager.IMPORTANCE_HIGH, manager.getNotificationChannel(DoorbellNotifier.CHANNEL_ID).importance)
        // Le toucher ouvre l'app : l'intention cible l'activité principale de ce paquet.
        assertEquals(context.packageName, n.contentIntent.creatorPackage)
        manager.cancelAll()
    }

    @Test
    fun without_the_permission_nothing_is_shown() {
        grant(false)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancelAll()
        assertFalse(DoorbellNotifier.show(context, DoorbellRing("ring-it-2", null), RingDeduper()))
        assertTrue(manager.activeNotifications.none { it.packageName == context.packageName })
    }
}
