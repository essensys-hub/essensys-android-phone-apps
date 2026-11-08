package com.essensys.android.data.push

import com.essensys.android.EssensysApp
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Réception des notifications. App au premier plan : on affiche la notification (dédoublonnée par `ring_id`).
 * App fermée ou en arrière-plan : FCM affiche lui-même le bloc notification sur le canal `doorbell` créé au démarrage.
 */
class EssensysMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val ring = DoorbellRing.parse(message.data, message.messageId) ?: return
        DoorbellNotifier.show(
            applicationContext, ring, DoorbellNotifier.deduper(applicationContext),
            title = message.notification?.title, body = message.notification?.body,
        )
    }

    /** Jeton renouvelé : on met à jour l'appareil déjà enregistré, avec le choix actuel. */
    override fun onNewToken(token: String) {
        val container = (application as? EssensysApp)?.container ?: return
        scope.launch { container.doorbell.onTokenRefreshed(token) }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
