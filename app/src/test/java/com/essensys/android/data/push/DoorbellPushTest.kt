package com.essensys.android.data.push

import com.essensys.android.data.http.ApiResult
import com.essensys.android.data.http.EssensysJson
import com.essensys.android.data.http.GatewayPresence
import com.essensys.android.data.http.PortalSession
import com.essensys.android.data.http.PushDeviceBody
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.support.TestServer
import com.essensys.android.ui.screens.DoorbellViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Notification de sonnette et option « Sonnette » (sonnette-plages-push-2026-11-008, tâches 6.1 et 6.2).
 * Serveur factice : aucun cloud, aucune notification et aucune armoire réels.
 *
 * NR: NR-android-28 essensys-hub/essensys-feature-lifecycle#61 (sub-issue essensys-hub/essensys-android-phone-apps#51) :
 * le champ `gateway_state` absent d'un ancien cloud ne casse rien, et la déconnexion supprime l'appareil du cloud.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DoorbellPushTest {

    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun tearDown() = Dispatchers.resetMain()

    private suspend fun waitUntil(block: () -> Boolean) = withTimeout(10_000) { while (!block()) delay(10) }

    // --- 6.1 réception -------------------------------------------------------------------------------------------

    @Test
    fun a_doorbell_message_is_parsed() {
        val ring = DoorbellRing.parse(mapOf("type" to "doorbell", "ring_id" to "r-1", "at" to "2026-11-08T10:15:30.123Z"))
        assertEquals(DoorbellRing("r-1", "2026-11-08T10:15:30.123Z"), ring)
    }

    @Test
    fun other_messages_are_ignored() {
        assertNull(DoorbellRing.parse(mapOf("type" to "promo", "ring_id" to "r-1")))
        assertNull(DoorbellRing.parse(emptyMap()))
    }

    @Test
    fun the_message_id_replaces_a_missing_ring_id() {
        assertEquals("m-9", DoorbellRing.parse(mapOf("type" to "doorbell"), fallbackId = "m-9")?.ringId)
        assertNull(DoorbellRing.parse(mapOf("type" to "doorbell")))
    }

    @Test
    fun two_messages_with_the_same_ring_id_make_one_notification() {
        val deduper = RingDeduper()
        assertTrue(deduper.firstTime("r-1"))
        assertFalse(deduper.firstTime("r-1"))
        assertTrue(deduper.firstTime("r-2"))
    }

    @Test
    fun the_deduper_survives_a_process_restart_and_stays_bounded() {
        var stored = emptyList<String>()
        val first = RingDeduper(capacity = 3, save = { stored = it })
        listOf("a", "b", "c", "d").forEach { assertTrue(first.firstTime(it)) }
        assertEquals(listOf("b", "c", "d"), stored) // « a » est sorti de la fenêtre
        val second = RingDeduper(capacity = 3, load = { stored }, save = { stored = it })
        assertFalse(second.firstTime("d"))
        assertTrue(second.firstTime("a"))
    }

    @Test
    fun the_runtime_permission_is_only_needed_from_android_13() {
        assertFalse(DoorbellNotifier.needsRuntimePermission(sdk = 32))
        assertTrue(DoorbellNotifier.needsRuntimePermission(sdk = 33))
    }

    // --- gateway_state -------------------------------------------------------------------------------------------

    private fun session(extra: String) = EssensysJson.decodeFromString(
        PortalSession.serializer(), """{"user":{"id":1,"email":"demo@essensys.fr"},"portal_access":true$extra}""",
    )

    @Test
    fun the_three_states_have_their_label() {
        assertEquals("Gateway en ligne", session(""","gateway_state":"online"""").gatewayPresence()?.label)
        assertEquals("Gateway hors ligne", session(""","gateway_state":"offline"""").gatewayPresence()?.label)
        assertEquals("Aucune gateway liée", session(""","gateway_state":"none"""").gatewayPresence()?.label)
    }

    // NR: NR-android-28 essensys-hub/essensys-feature-lifecycle#61 : un cloud sans le champ garde l'affichage actuel.
    @Test
    fun a_missing_or_unknown_gateway_state_keeps_the_current_display_NR_android_28() {
        val old = session(""","gateway":{"id":"gw","online":true}""")
        assertNull(old.gatewayPresence())
        assertTrue(old.armoireOnline())
        assertNull(session(""","gateway_state":"surprise"""").gatewayPresence())
    }

    @Test
    fun the_option_is_visible_only_with_a_gateway_in_cloud_mode() {
        assertFalse(DoorbellViewModel.optionVisible(ConnectionMode.CLOUD, GatewayPresence.NONE))
        assertFalse(DoorbellViewModel.optionVisible(ConnectionMode.CLOUD, null))
        assertTrue(DoorbellViewModel.optionVisible(ConnectionMode.CLOUD, GatewayPresence.ONLINE))
        assertTrue(DoorbellViewModel.optionVisible(ConnectionMode.CLOUD, GatewayPresence.OFFLINE))
        assertFalse(DoorbellViewModel.optionVisible(ConnectionMode.LAN, GatewayPresence.ONLINE))
    }

    // --- 6.2 option Sonnette -------------------------------------------------------------------------------------

    private fun repo(t: TestServer, token: String? = "tok-123") = DoorbellRepository(t.container.portal, t.store, { token }, appVersion = "2.9.0")

    @Test
    fun enabling_registers_the_device_with_doorbell_notify_true() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, """{"id":"dev-7","platform":"android","doorbell_notify":true}""")
            val outcome = repo(t).set(true)
            assertEquals(DoorbellOutcome.Done(true), outcome)
            val req = t.server.takeRequest()
            assertEquals("PUT", req.method)
            assertEquals("/api/portal/push/devices", req.url.encodedPath)
            assertEquals("Bearer jwt", req.headers["Authorization"])
            assertEquals(
                """{"platform":"android","token":"tok-123","app_version":"2.9.0","doorbell_notify":true}""",
                req.body!!.utf8(),
            )
            assertEquals("dev-7", t.store.state.value.pushDeviceId)
            assertTrue(t.store.state.value.doorbellNotify)
        }
    }

    @Test
    fun disabling_sends_doorbell_notify_false_and_touches_nothing_else() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, """{"id":"dev-7","platform":"android","doorbell_notify":false}""")
            assertEquals(DoorbellOutcome.Done(false), repo(t).set(false))
            val req = t.server.takeRequest()
            assertTrue(req.body!!.utf8().contains(""""doorbell_notify":false"""))
            assertEquals(1, t.server.requestCount) // aucune commande, aucune lumière
        }
    }

    @Test
    fun without_a_push_token_nothing_is_sent() = runBlocking {
        TestServer(token = "jwt").use { t ->
            assertEquals(DoorbellOutcome.Unavailable, repo(t, token = null).set(true))
            assertEquals(0, t.server.requestCount)
        }
    }

    @Test
    fun the_state_is_read_again_from_the_cloud() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.store.update { it.copy(pushDeviceId = "dev-7") }
            t.enqueue(200, """{"available":true,"devices":[{"id":"dev-7","platform":"android","doorbell_notify":true}]}""")
            assertEquals(true, repo(t).refresh())
            assertTrue(t.store.state.value.doorbellNotify)
            // Appareil supprimé côté cloud : l'interrupteur repart à zéro.
            t.enqueue(200, """{"available":true,"devices":[]}""")
            assertEquals(false, repo(t).refresh())
            assertNull(t.store.state.value.pushDeviceId)
            // Cloud injoignable : l'état connu est conservé (réponse nulle).
            t.enqueue(500, "boom")
            assertNull(repo(t).refresh())
        }
    }

    // NR: NR-android-28 essensys-hub/essensys-feature-lifecycle#61 : la déconnexion supprime l'appareil côté cloud.
    @Test
    fun logout_deletes_the_device_from_the_cloud_NR_android_28() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.store.update { it.copy(pushDeviceId = "dev-7", doorbellNotify = true) }
            t.enqueue(204)
            t.container.portal.unregisterPushDevice()
            val req = t.server.takeRequest()
            assertEquals("DELETE", req.method)
            assertEquals("/api/portal/push/devices/dev-7", req.url.encodedPath)
            assertNull(t.store.state.value.pushDeviceId)
            assertFalse(t.store.state.value.doorbellNotify)
        }
    }

    @Test
    fun logout_without_a_registered_device_sends_nothing() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.container.portal.unregisterPushDevice()
            assertEquals(0, t.server.requestCount)
        }
    }

    @Test
    fun a_refreshed_token_updates_the_registered_device_with_the_current_choice() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.store.update { it.copy(pushDeviceId = "dev-7", doorbellNotify = false) }
            t.enqueue(200, """{"id":"dev-7","platform":"android","doorbell_notify":false}""")
            repo(t).onTokenRefreshed("tok-new")
            val body = t.server.takeRequest().body!!.utf8()
            assertTrue(body.contains(""""token":"tok-new"""") && body.contains(""""doorbell_notify":false"""))
        }
    }

    @Test
    fun a_refreshed_token_without_a_registered_device_is_ignored() = runBlocking {
        TestServer(token = "jwt").use { t ->
            repo(t).onTokenRefreshed("tok-new")
            assertEquals(0, t.server.requestCount)
        }
    }

    @Test
    fun a_denied_permission_resets_the_switch_and_points_to_system_settings() = runBlocking {
        TestServer(token = "jwt").use { t ->
            val vm = DoorbellViewModel(repo(t))
            vm.permissionDenied()
            assertFalse(vm.state.value.enabled)
            assertTrue(vm.state.value.feedback!!.text.contains("réglages du système"))
            assertEquals(0, t.server.requestCount)
        }
    }

    @Test
    fun a_build_without_firebase_explains_instead_of_enabling() = runBlocking {
        TestServer(token = "jwt").use { t ->
            val vm = DoorbellViewModel(repo(t, token = null))
            vm.enable()
            waitUntil { !vm.state.value.busy }
            assertFalse(vm.state.value.enabled)
            assertEquals(DoorbellViewModel.UNAVAILABLE, vm.state.value.feedback!!.text)
        }
    }

    @Test
    fun the_toggle_follows_the_cloud_answer() = runBlocking {
        TestServer(token = "jwt").use { t ->
            val vm = DoorbellViewModel(repo(t))
            t.enqueue(200, """{"id":"dev-7","platform":"android","doorbell_notify":true}""")
            vm.enable()
            waitUntil { vm.state.value.enabled }
            t.enqueue(200, """{"id":"dev-7","platform":"android","doorbell_notify":false}""")
            vm.disable()
            waitUntil { !vm.state.value.enabled }
            t.enqueue(500, "boom")
            vm.enable()
            waitUntil { vm.state.value.feedback != null }
            assertFalse(vm.state.value.enabled)
        }
    }

    @Test
    fun the_token_never_appears_in_a_printed_body() {
        val body = PushDeviceBody("android", "secret-token-xyz", "2.9.0", true)
        assertFalse(body.toString().contains("secret-token-xyz"))
        assertNotNull(body.toString())
    }
}
