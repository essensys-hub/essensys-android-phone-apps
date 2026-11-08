package com.essensys.android.data.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Fournit le jeton push de l'appareil ; nul quand les notifications ne sont pas disponibles. */
fun interface PushTokenSource {
    suspend fun token(): String?
}

/**
 * Jeton Firebase. Sans `google-services.json` à la compilation, Firebase n'est pas initialisé : aucun jeton, l'app
 * fonctionne sans notification (le build reste vert, le fichier n'est jamais versionné).
 */
class FirebaseTokenSource(private val context: Context) : PushTokenSource {
    override suspend fun token(): String? {
        if (FirebaseApp.getApps(context).isEmpty()) return null
        return runCatching {
            suspendCoroutine<String?> { cont ->
                FirebaseMessaging.getInstance().token.addOnCompleteListener { cont.resume(if (it.isSuccessful) it.result else null) }
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
