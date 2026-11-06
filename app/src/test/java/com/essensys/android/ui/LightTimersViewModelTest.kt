package com.essensys.android.ui

import com.essensys.android.data.control.LightTimer
import com.essensys.android.support.TestServer
import com.essensys.android.ui.screens.LightTimersViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LightTimersViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun timers(vararg pairs: Pair<String, Int?>) =
        """{"timers":[${pairs.joinToString(",") { (id, m) -> """{"id":"$id","minutes":${m ?: "null"}}""" }}]}"""

    private suspend fun waitUntil(block: () -> Boolean) = withTimeout(10_000) { while (!block()) delay(10) }

    @Test
    fun recheck_delays_are_5_10_and_20_seconds_after_the_send() {
        assertEquals(listOf(5_000L, 10_000L, 20_000L), LightTimersViewModel.RECHECK_MS.runningFold(0L) { a, b -> a + b }.drop(1))
    }

    @Test
    fun load_shows_unknown_for_unread_values() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, timers("dressing" to 15, "wc1" to null))
            val vm = LightTimersViewModel(t.container.comfort)
            vm.load()
            waitUntil { vm.state.value.minutes[LightTimer.DRESSING] == 15 }
            assertNull(vm.state.value.minutes[LightTimer.WC1])
        }
    }

    @Test
    fun sent_value_is_read_back_until_the_cabinet_confirms() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, """{"id":"dressing","minutes":7,"status":"queued"}""")
            t.enqueue(200, timers("dressing" to 15)) // 1re relecture : pas encore appliqué
            t.enqueue(200, timers("dressing" to 7)) // 2e relecture : confirmé, les suivantes sont inutiles
            val vm = LightTimersViewModel(t.container.comfort, recheckMs = listOf(20L, 20L, 20L))
            vm.set(LightTimer.DRESSING, 7)
            waitUntil { vm.state.value.minutes[LightTimer.DRESSING] == 7 && vm.state.value.waiting.isEmpty() }
            assertEquals(3, t.server.requestCount) // PUT + 2 GET, pas de 3e relecture
            assertEquals("PUT", t.server.takeRequest().method)
        }
    }

    @Test
    fun dry_run_is_neither_confirmed_nor_read_back() = runBlocking {
        TestServer(token = "jwt", testMode = true).use { t ->
            t.enqueue(200, """{"id":"wc2","minutes":45,"status":"dry_run"}""")
            val vm = LightTimersViewModel(t.container.comfort, recheckMs = listOf(20L))
            vm.set(LightTimer.WC2, 45)
            waitUntil { vm.state.value.feedback != null }
            delay(100)
            assertEquals(1, t.server.requestCount)
            assertEquals(true, vm.state.value.feedback!!.text.contains("non exécutée"))
        }
    }

    @Test
    fun invalid_value_sends_nothing() = runBlocking {
        TestServer(token = "jwt").use { t ->
            val vm = LightTimersViewModel(t.container.comfort)
            vm.set(LightTimer.DRESSING, 300)
            delay(100)
            assertEquals(0, t.server.requestCount)
        }
    }
}
