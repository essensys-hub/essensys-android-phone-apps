package com.essensys.android.data.control

import com.essensys.android.data.http.ApiError
import com.essensys.android.data.http.ApiResult
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.support.TestServer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// runBlocking (temps réel) : le polling de synchronisation et OkHttp ne se mélangent pas au temps virtuel.
class HeatingRepositoryTest {

    private val jour = IndexTable.heatingZones.first { it.id == "jour" }
    private val sdb1 = IndexTable.heatingZones.first { it.id == "sdb1" }
    private val forty = (13 until 53).map { Injection(it, "17") }

    @Test
    fun read_schedule_decodes_zone_and_reports_stale() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, t.fixture("exchange_stale.json"))
            val read = (t.container.heating.readSchedule(jour) as ApiResult.Ok).value
            assertTrue(read.stale)
            assertEquals(1, read.grid[0, 0]) // 17 = 0x11
            assertEquals(1, read.grid[0, 1])
            assertTrue(t.server.takeRequest().url.toString().endsWith("keys=" + (13..96).joinToString(",")))
        }
    }

    @Test
    fun save_sends_chunks_in_order_and_reports_progress() = runBlocking {
        TestServer(token = "jwt").use { t ->
            repeat(2) { t.enqueue(200, t.fixture("inject_batch_ok.json")) }
            val events = t.container.heating.save(forty).toList()
            assertEquals(
                listOf(
                    SaveEvent.Start(40, 2),
                    SaveEvent.Sending(1, 2, 13, 42), SaveEvent.ChunkSent(1, 2),
                    SaveEvent.Sending(2, 2, 43, 52), SaveEvent.ChunkSent(2, 2),
                    SaveEvent.Done(InjectOutcome.SENT),
                ),
                events,
            )
            assertTrue(t.server.takeRequest().body!!.utf8().startsWith("""{"params":[{"k":13,"""))
            assertTrue(t.server.takeRequest().body!!.utf8().startsWith("""{"params":[{"k":43,"""))
        }
    }

    @Test
    fun failed_chunk_stops_sending_and_keeps_pending_bytes() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, t.fixture("inject_batch_ok.json"))
            t.enqueue(429, "Rate limit exceeded\n")
            val failed = t.container.heating.save(forty).toList().last() as SaveEvent.Failed
            assertEquals(2, failed.chunkIndex)
            assertEquals(ApiError.RateLimited, failed.error)
            assertEquals(forty.take(30), failed.sent)
            assertEquals(forty.drop(30), failed.pending)
            assertEquals(2, t.server.requestCount)
        }
    }

    // NR: NR-android-14 essensys-hub/essensys-android-phone-apps#12
    @Test
    fun immediate_mode_sends_zone_mode_index_NR_android_14() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, t.fixture("inject_ok.json"))
            t.container.heating.setMode(sdb1, IndexTable.ImmediateMode.FORCE_CONFORT)
            assertEquals("""{"k":351,"v":"17"}""", t.server.takeRequest().body!!.utf8())
        }
    }

    @Test
    fun lan_sync_polls_status_then_rereads_schedule() = runBlocking {
        TestServer(mode = ConnectionMode.LAN).use { t ->
            t.store.update { it.copy(lanCookie = "abc") }
            t.container.withHeatingSyncTiming(pollMs = 10, timeoutMs = 1_000)
            t.enqueue(200, t.fixture("heating_sync_started.json"))
            t.enqueue(200, """{"active":true,"received":30,"total":84,"chunksTotal":3,"chunksCompleted":1}""")
            t.enqueue(200, t.fixture("heating_sync_done.json"))
            t.enqueue(200, """{"values":[{"k":13,"v":"33"}]}""")
            val events = t.container.heating.syncFromArmoire(jour).toList()
            assertEquals(
                listOf(SyncEvent.Progress(0, 84), SyncEvent.Progress(30, 84), SyncEvent.Progress(84, 84)),
                events.dropLast(1),
            )
            val done = events.last() as SyncEvent.Done
            assertTrue(done.complete)
            assertEquals(1, done.read.grid[0, 0])
            val start = t.server.takeRequest()
            assertEquals("/api/admin/heating/sync", start.url.encodedPath)
            assertEquals("""{"startIndex":13,"byteCount":84}""", start.body!!.utf8())
            assertEquals("startIndex=13&byteCount=84", t.server.takeRequest().url.encodedQuery)
        }
    }

    @Test
    fun lan_sync_busy_is_reported() = runBlocking {
        TestServer(mode = ConnectionMode.LAN).use { t ->
            t.enqueue(409, t.fixture("heating_sync_busy.json"))
            assertEquals(listOf(SyncEvent.Busy), t.container.heating.syncFromArmoire(jour).toList())
        }
    }

    @Test
    fun cloud_sync_only_rereads_cache() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, t.fixture("exchange_stale.json"))
            val event = t.container.heating.syncFromArmoire(jour).toList().single()
            assertTrue(event is SyncEvent.CacheOnly)
            assertEquals("/api/portal/exchange", t.server.takeRequest().url.encodedPath)
        }
    }
}
