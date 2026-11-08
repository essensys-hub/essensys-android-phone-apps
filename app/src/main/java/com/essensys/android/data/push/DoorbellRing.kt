package com.essensys.android.data.push

/**
 * Appui de sonnette reçu par notification (contrat cloud : données `{"type":"doorbell","ring_id","at"}`).
 * Sonnette-plages-push-2026-11-008, essensys-feature-lifecycle#61.
 */
data class DoorbellRing(val ringId: String, val at: String?) {
    companion object {
        const val TYPE = "doorbell"
        const val DEFAULT_TITLE = "Quelqu'un sonne"
        const val DEFAULT_BODY = "Sonnette de votre maison"

        /** Nul si le message n'est pas une sonnette. Sans `ring_id`, [fallbackId] (identifiant du message FCM) dédoublonne. */
        fun parse(data: Map<String, String>, fallbackId: String? = null): DoorbellRing? {
            if (data["type"] != TYPE) return null
            val id = data["ring_id"]?.takeIf { it.isNotBlank() } ?: fallbackId?.takeIf { it.isNotBlank() } ?: return null
            return DoorbellRing(id, data["at"])
        }
    }
}

/**
 * Dédoublonnage par `ring_id` : le cloud peut renvoyer le même appui. Mémorise les derniers identifiants
 * (persistés par [load] et [save], pour survivre à l'arrêt du processus).
 */
class RingDeduper(
    private val capacity: Int = 50,
    private val load: () -> List<String> = { emptyList() },
    private val save: (List<String>) -> Unit = {},
) {
    private val seen = LinkedHashSet<String>()

    @Synchronized
    fun firstTime(ringId: String): Boolean {
        if (seen.isEmpty()) seen.addAll(load())
        if (!seen.add(ringId)) return false
        while (seen.size > capacity) seen.remove(seen.first())
        save(seen.toList())
        return true
    }
}
