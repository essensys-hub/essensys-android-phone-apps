package com.essensys.android.data.support

import com.essensys.android.data.http.EssensysJson
import com.essensys.android.data.session.SessionStore
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Ce que l'utilisateur envoie : le zip, son résumé lisible et l'objet du message. */
data class SupportPackage(val zip: File, val summary: String, val subject: String)

/**
 * Bundle zip du support (spec support-bundle « Contenu et confidentialité du bundle ») :
 * `resume.txt`, `diagnostics.json`, `journal.jsonl`. Construit par liste blanche (décision C4) ; en
 * dernier rempart, les secrets connus de la session (jeton, cookie LAN) sont masqués partout.
 */
class SupportBundle(
    private val dir: File,
    private val store: SessionStore,
    private val log: AppLog,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    fun create(report: DiagnosticReport): SupportPackage {
        val generated = Instant.parse(report.generatedAt).atZone(zone)
        val summary = summary(report)
        val files = linkedMapOf(
            "resume.txt" to summary,
            "diagnostics.json" to PrettyJson.encodeToString(DiagnosticReport.serializer(), report),
            "journal.jsonl" to log.toJsonLines(),
        ).mapValues { (_, text) -> redact(text) }

        dir.mkdirs()
        dir.listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(".zip") }?.forEach { it.delete() }
        val zip = File(dir, "$PREFIX${report.appVersion}-${generated.format(FILE_STAMP)}.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            files.forEach { (name, text) ->
                out.putNextEntry(ZipEntry(name))
                out.write(text.toByteArray(Charsets.UTF_8))
                out.closeEntry()
            }
        }
        val subject = "Support Essensys — ${report.appVersion} — ${generated.format(SUBJECT_STAMP)}"
        return SupportPackage(zip, files.getValue("resume.txt"), subject)
    }

    /** Résumé lisible, montré à l'utilisateur avant l'envoi et repris dans le corps du message. */
    fun summary(report: DiagnosticReport): String = buildString {
        val generated = Instant.parse(report.generatedAt).atZone(zone)
        appendLine("Diagnostic Essensys — Android ${report.appVersion} (build ${report.appBuild})")
        appendLine("Généré le ${generated.format(SUBJECT_STAMP)}")
        report.checks.groupBy { it.group }.forEach { (group, checks) ->
            appendLine()
            appendLine(group)
            checks.forEach { appendLine("- [${it.checkStatus.label}] ${it.label} : ${it.detail}") }
        }
    }.trimEnd()

    private fun redact(text: String): String {
        val state = store.state.value
        return listOfNotNull(state.token, state.lanCookie)
            .filter { it.length >= 6 }
            .fold(text) { acc, secret -> acc.replace(secret, "•••") }
    }

    companion object {
        const val PREFIX = "essensys-support-android-"
        private val FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        private val SUBJECT_STAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        private val PrettyJson = Json(EssensysJson) { prettyPrint = true }
    }
}
