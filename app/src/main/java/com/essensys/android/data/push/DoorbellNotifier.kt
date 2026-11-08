package com.essensys.android.data.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.essensys.android.MainActivity
import com.essensys.android.R

/** Canal, permission et affichage de la notification « Quelqu'un sonne ». Aucune donnée d'armoire ni image. */
object DoorbellNotifier {
    /** Identifiant imposé par le cloud (`channel_id` de la notification FCM). */
    const val CHANNEL_ID = "doorbell"
    private const val PREFS = "doorbell_rings"
    private const val KEY = "seen"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.doorbell_channel_name), NotificationManager.IMPORTANCE_HIGH)
            .apply { description = context.getString(R.string.doorbell_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Android 13 et plus : permission `POST_NOTIFICATIONS` à demander ; avant, aucune permission d'exécution. */
    fun needsRuntimePermission(sdk: Int = Build.VERSION.SDK_INT) = sdk >= Build.VERSION_CODES.TIRAMISU

    /** Les notifications de l'app sont autorisées (permission accordée et non coupées dans les réglages). */
    fun allowed(context: Context): Boolean {
        if (needsRuntimePermission() &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun deduper(context: Context): RingDeduper {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return RingDeduper(
            load = { prefs.getString(KEY, "")?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList() },
            save = { prefs.edit().putString(KEY, it.joinToString("\n")).apply() },
        )
    }

    /** Un appui déjà vu n'affiche rien ; deux notifications de même `ring_id` partagent d'ailleurs le même identifiant. */
    fun show(context: Context, ring: DoorbellRing, deduper: RingDeduper, title: String? = null, body: String? = null): Boolean {
        if (!deduper.firstTime(ring.ringId)) return false
        if (!allowed(context)) return false
        ensureChannel(context)
        // Ouvre l'écran d'accueil : l'activité principale démarre à la racine de l'app.
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_doorbell)
            .setContentTitle(title?.takeIf { it.isNotBlank() } ?: DoorbellRing.DEFAULT_TITLE)
            .setContentText(body?.takeIf { it.isNotBlank() } ?: DoorbellRing.DEFAULT_BODY)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        @Suppress("MissingPermission") // allowed() vient de vérifier la permission
        NotificationManagerCompat.from(context).notify(ring.ringId, 1, notification)
        return true
    }
}
