package com.essensys.android.data.control

import com.essensys.android.data.http.ApiError

/**
 * Nom d'un volet ou d'un store (spec shutter-names-clients, contrat du change `noms-volets-2026-11-005`). [name] est
 * nul tant qu'aucun nom n'a été défini ; le nom affiché est [name] sinon [defaultName]. Texte brut : jamais interprété.
 */
data class ShutterName(
    val id: String,
    val defaultName: String,
    val name: String?,
    val version: Int = 0,
    val updatedAt: String? = null,
) {
    val display: String get() = name?.takeIf { it.isNotBlank() } ?: defaultName
}

/** Résultat d'une écriture : le volet renvoyé par le serveur (si lisible) et le mode essai à blanc. */
data class ShutterNameSaved(val shutter: ShutterName?, val dryRun: Boolean)

object ShutterNames {
    const val MAX_LENGTH = LightNames.MAX_LENGTH

    /** Noms d'origine (`IndexTable.shutters`), utilisés tant que la lecture n'a pas abouti ou si elle échoue. */
    fun fallback(): Map<String, ShutterName> =
        IndexTable.shutters.associate { it.id to ShutterName(it.id, it.name, null) }

    /** Même règle que les lumières : 1 à 40 caractères après rognage, sans contrôle ni `<` `>`. */
    fun validate(raw: String): Result<String> = LightNames.validate(raw)

    const val INVALID = LightNames.INVALID

    /** Messages des erreurs 400, 403 et 503 de l'écriture ; les autres gardent le message commun. */
    fun errorMessage(error: ApiError): String = when {
        error is ApiError.Http && error.code == "unknown_shutter" -> "Volet inconnu."
        else -> LightNames.errorMessage(error)
    }
}
