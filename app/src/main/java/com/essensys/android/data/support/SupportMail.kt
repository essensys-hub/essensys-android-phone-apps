package com.essensys.android.data.support

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.essensys.android.BuildConfig

/**
 * Ouvre l'appli email de l'utilisateur (design D4) : destinataire support, objet, résumé et zip joint.
 * Rien n'est envoyé sans lui. Sans appli email, repli sur la feuille de partage.
 */
object SupportMail {
    const val ADDRESS = BuildConfig.SUPPORT_EMAIL

    fun authority(context: Context) = "${context.packageName}.support"

    /** Intention d'envoi, sélecteur `mailto:` pour ne proposer que les applis email. */
    fun emailIntent(context: Context, pkg: SupportPackage): Intent =
        shareIntent(context, pkg).apply { selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")) }

    fun shareIntent(context: Context, pkg: SupportPackage): Intent {
        val uri = FileProvider.getUriForFile(context, authority(context), pkg.zip)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(ADDRESS))
            putExtra(Intent.EXTRA_SUBJECT, pkg.subject)
            putExtra(Intent.EXTRA_TEXT, pkg.summary)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun send(context: Context, pkg: SupportPackage) {
        try {
            context.startActivity(emailIntent(context, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            val chooser = Intent.createChooser(shareIntent(context, pkg), "Envoyer le diagnostic")
            context.startActivity(chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
