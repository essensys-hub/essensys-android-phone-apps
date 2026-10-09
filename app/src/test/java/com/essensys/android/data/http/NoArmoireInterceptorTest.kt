package com.essensys.android.data.http

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NoArmoireInterceptorTest {

    private val server = MockWebServer()
    private val client = OkHttpClient.Builder().addInterceptor(NoArmoireInterceptor()).build()
    private val json = "application/json".toMediaType()

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.close()

    // NR: NR-android-1 essensys-hub/essensys-android-phone-apps#1
    @Test
    fun no_armoire_interceptor_blocks_real_mutation_NR_android_1() {
        val request = Request.Builder()
            .url("https://mon.essensys.fr/api/portal/inject")
            .post("""{"k":612,"v":"128"}""".toRequestBody(json))
            .build()
        val error = runCatching { client.newCall(request).execute() }.exceptionOrNull()
        assertTrue(error is ArmoireMutationBlocked)
        assertTrue(error!!.message!!.startsWith("no-armoire"))
    }

    @Test
    fun mutation_towards_local_mock_server_is_allowed() {
        server.enqueue(MockResponse.Builder().code(200).body("""{"success":true}""").build())
        val request = Request.Builder()
            .url(server.url("/api/portal/inject"))
            .post("""{"k":612,"v":"128"}""".toRequestBody(json))
            .build()
        client.newCall(request).execute().use { assertEquals(200, it.code) }
    }

    @Test
    fun dry_run_mutation_is_not_blocked_by_guard() {
        val request = Request.Builder()
            .url("https://mon.essensys.fr/api/portal/inject")
            .header(NoArmoireInterceptor.TEST_MODE_HEADER, NoArmoireInterceptor.DRY_RUN)
            .post("{}".toRequestBody(json))
            .build()
        // Aucun appel réseau : on interroge la garde directement.
        assertFalse(NoArmoireInterceptor().shouldBlock(request))
    }

    @Test
    fun reads_and_non_armoire_paths_are_not_mutations() {
        assertFalse(NoArmoireInterceptor.isArmoireMutation("GET", "/api/portal/inject"))
        assertFalse(NoArmoireInterceptor.isArmoireMutation("POST", "/api/auth/login"))
        assertTrue(NoArmoireInterceptor.isArmoireMutation("POST", "/api/admin/inject"))
        assertTrue(NoArmoireInterceptor.isArmoireMutation("POST", "/api/portal/scenarios/3/launch"))
        assertTrue(NoArmoireInterceptor.isArmoireMutation("POST", "/api/portal/web/actions"))
    }
}
