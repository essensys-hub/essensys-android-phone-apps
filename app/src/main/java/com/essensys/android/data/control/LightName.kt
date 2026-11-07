package com.essensys.android.data.control

import com.essensys.android.data.http.ApiError

/**
 * Nom d'une lumière (spec light-names-clients, contrat du change `noms-lumieres-2026-11-004`). [name] est nul tant
 * qu'aucun nom n'a été défini ; le nom affiché est [name] sinon [defaultName]. Texte brut : jamais interprété.
 */
data class LightName(
    val id: String,
    val defaultName: String,
    val name: String?,
    val version: Int = 0,
    val updatedAt: String? = null,
) {
    val display: String get() = name?.takeIf { it.isNotBlank() } ?: defaultName
}

/** Résultat d'une écriture : la lumière renvoyée par le serveur (si lisible) et le mode essai à blanc. */
data class LightNameSaved(val light: LightName?, val dryRun: Boolean)

object LightNames {
    const val MAX_LENGTH = 40

    /** Noms d'origine (`IndexTable.lights`), utilisés tant que la lecture n'a pas abouti ou si elle échoue. */
    fun fallback(): Map<String, LightName> =
        IndexTable.lights.associate { it.id to LightName(it.id, it.name, null) }

    /**
     * Règle du nom : après rognage, 1 à 40 caractères Unicode, sans caractère de contrôle (U+0000 à U+001F, U+007F)
     * ni `<` `>`. Vide = refusé ici (la remise à zéro passe par « Nom d'origine »).
     */
    fun validate(raw: String): Result<String> {
        val name = raw.trim()
        val length = name.codePointCount(0, name.length)
        return when {
            length < 1 || length > MAX_LENGTH -> Result.failure(IllegalArgumentException(INVALID))
            name.any { it.code <= 0x1F || it.code == 0x7F || it == '<' || it == '>' } -> Result.failure(IllegalArgumentException(INVALID))
            else -> Result.success(name)
        }
    }

    const val INVALID = "Nom invalide : 1 à 40 caractères, sans < ni >."

    /** Messages des erreurs 400, 403 et 503 de l'écriture ; les autres gardent le message commun. */
    fun errorMessage(error: ApiError): String = when {
        error is ApiError.Forbidden -> "Réservé à l'administrateur de l'armoire."
        error is ApiError.Http && error.code == "invalid_name" -> INVALID
        error is ApiError.Http && error.code == "unknown_light" -> "Lumière inconnue."
        error is ApiError.Http && (error.code == "names_unavailable" || error.status == 503) ->
            "Stockage des noms indisponible sur la gateway, réessayez plus tard."
        else -> error.userMessage
    }
}
