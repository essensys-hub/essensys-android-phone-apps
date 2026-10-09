package com.essensys.android.data.http

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * Garde no-armoire : bloque toute mutation domotique vers un hôte réel.
 *
 * Portage de `essensys-plugin-framework/ts/src/noArmoire.ts`. Installée dans les builds de test
 * (et activable en debug) : seules les requêtes vers [allowedHosts] (serveur simulé local) ou
 * explicitement en dry-run passent. Spec : android-quality « Garde no-armoire ».
 */
class NoArmoireInterceptor(
    private val allowedHosts: Set<String> = setOf("127.0.0.1", "localhost", "10.0.2.2"),
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (shouldBlock(request)) {
            throw ArmoireMutationBlocked("no-armoire : mutation réelle interdite (${request.method} ${request.url})")
        }
        return chain.proceed(request)
    }

    fun shouldBlock(request: Request): Boolean {
        val url = request.url
        val dryRun = request.header(TEST_MODE_HEADER).equals(DRY_RUN, ignoreCase = true) ||
            url.queryParameter("test_mode") == "dry_run"
        return isArmoireMutation(request.method, url.encodedPath) && !dryRun && url.host !in allowedHosts
    }

    companion object {
        const val TEST_MODE_HEADER = "X-Essensys-Test-Mode"
        const val DRY_RUN = "dry-run"

        private val ARMOIRE_MUTATIONS = listOf(
            Regex("/api/admin/inject"),
            Regex("/api/portal/inject"),
            Regex("/api/(portal/)?web/actions"),
            Regex("/scenarios/[^/]+/launch"),
        )
        private val MUTATING = setOf("POST", "PUT", "PATCH", "DELETE")

        fun isArmoireMutation(method: String, path: String): Boolean =
            method.uppercase() in MUTATING && ARMOIRE_MUTATIONS.any { it.containsMatchIn(path) }
    }
}

class ArmoireMutationBlocked(message: String) : IOException(message)
