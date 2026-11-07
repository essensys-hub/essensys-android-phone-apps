package com.essensys.android.data.control

/**
 * Temporisation d'extinction automatique d'une lumière (spec light-timers-clients). Une case de la table d'échange
 * par lumière, un octet : 1 à 255 minutes, 0 = jamais. Clés verrouillées par NR-android-24
 * (design du change `lumieres-temporisees-2026-11-003`, « Mapping retenu »).
 *
 * L'identifiant est celui de [IndexTable.lights] ; l'ordre de l'énumération est celui de la réponse du serveur.
 */
enum class LightTimer(val id: String, val label: String, val key: Int) {
    DRESSING("dressing", "Dressing", 521),
    WC1("wc1", "WC 1", 555),
    WC2("wc2", "WC 2", 556),
    SERVICE("pieceserv", "Service", 557),
    ANNEXE1("ann1", "Annexe 1", 561),
    ANNEXE2("ann2", "Annexe 2", 562);

    companion object {
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 255
        const val NEVER = 0

        /** Raccourcis de l'écran de l'armoire : (minutes, libellé). */
        val SHORTCUTS: List<Pair<Int, String>> = listOf(15 to "15 min", 30 to "30 min", 60 to "1 h", NEVER to "Jamais")

        fun byId(id: String?): LightTimer? = entries.firstOrNull { it.id == id }

        /** Valeur valide pour le serveur : 0 (jamais) à 255. */
        fun isValid(minutes: Int) = minutes in NEVER..MAX_MINUTES

        /**
         * Saisie du champ libre : 1 à 255 entier. 0 se choisit par « Jamais », 300 ou un texte sont refusés (null)
         * avant tout envoi.
         */
        fun parseFree(raw: String): Int? = raw.trim().toIntOrNull()?.takeIf { it in MIN_MINUTES..MAX_MINUTES }

        /** « 15 min », « 1 h », « Jamais », « 7 min » (valeur non raccourcie, telle quelle), « inconnue ». */
        fun describe(minutes: Int?): String = when (minutes) {
            null -> "inconnue"
            NEVER -> "Jamais"
            else -> SHORTCUTS.firstOrNull { it.first == minutes }?.second ?: "$minutes min"
        }
    }
}
