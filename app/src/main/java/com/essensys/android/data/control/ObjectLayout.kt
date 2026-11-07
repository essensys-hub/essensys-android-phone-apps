package com.essensys.android.data.control

import com.essensys.android.data.http.ApiError

/** Genre d'objets dont la disposition est gérée : les 33 lumières ou les 16 volets et stores. */
enum class LayoutKind(val path: String, val unknownCode: String) {
    LIGHTS("lights", "unknown_light"),
    SHUTTERS("shutters", "unknown_shutter");

    /** Identifiants du catalogue, dans l'ordre d'origine (`IndexTable`, jamais modifié par la disposition). */
    fun catalogue(): List<String> = when (this) {
        LIGHTS -> IndexTable.lights.map { it.id }
        SHUTTERS -> IndexTable.shutters.map { it.id }
    }

    fun groupOf(id: String): IndexTable.Group? = when (this) {
        LIGHTS -> IndexTable.lights.firstOrNull { it.id == id }?.group
        SHUTTERS -> IndexTable.shutters.firstOrNull { it.id == id }?.group
    }
}

/**
 * Disposition d'un genre d'objets (spec object-layout-clients, contrat du change `affichage-objets-2026-11-006`) :
 * `order` (identifiants, éventuellement incomplet) et `disabled` (objets masqués). Version 0 = disposition par défaut.
 */
data class ObjectLayout(
    val order: List<String> = emptyList(),
    val disabled: List<String> = emptyList(),
    val version: Int = 0,
    val updatedAt: String? = null,
) {
    companion object {
        val DEFAULT = ObjectLayout()
    }
}

/** Résultat d'une écriture : le document renvoyé par le serveur (si lisible) et le mode essai à blanc. */
data class ObjectLayoutSaved(val layout: ObjectLayout?, val dryRun: Boolean)

/**
 * Disposition effective (règle commune aux quatre clients, `effectiveLayout`) : [ordered] contient tous les
 * identifiants du catalogue (ordre de `order` d'abord, puis les absents dans l'ordre du catalogue), [disabled] les
 * identifiants désactivés du catalogue. Les identifiants inconnus sont ignorés.
 */
data class EffectiveLayout(val ordered: List<String>, val disabled: Set<String>) {

    fun isEnabled(id: String) = id !in disabled

    /**
     * [items] sans les objets désactivés, triés selon l'ordre global. Appliqué à la liste d'un groupe, il ne déplace
     * jamais un objet d'un groupe à l'autre ; appliqué à tout le genre, il donne l'ordre des écrans sans groupe.
     */
    fun <T> visible(items: List<T>, idOf: (T) -> String): List<T> {
        val rank = ordered.withIndex().associate { (index, id) -> id to index }
        return items.filter { idOf(it) !in disabled }.sortedBy { rank[idOf(it)] ?: Int.MAX_VALUE }
    }

    /** Vrai si l'ordre effectif diffère de l'ordre du catalogue d'origine (au moins un Monter/Descendre enregistré). */
    fun isReordered(catalogue: List<String>) = ordered != catalogue

    /**
     * Écran sans groupe qui a son propre ordre (Temporisations) : tant que l'ordre des objets n'a pas été
     * personnalisé, [items] garde son ordre propre (filtré des désactivés) ; sinon l'ordre global filtré s'applique.
     */
    fun <T> visibleKeepingOwnOrder(items: List<T>, catalogue: List<String>, idOf: (T) -> String): List<T> =
        if (isReordered(catalogue)) visible(items, idOf) else items.filter { idOf(it) !in disabled }

    /** Échange [id] avec son voisin du même groupe ([delta] -1 monte, +1 descend) ; sans effet en tête ou en queue de groupe. */
    fun move(id: String, delta: Int, groupOf: (String) -> IndexTable.Group?): EffectiveLayout {
        val group = groupOf(id)
        val sameGroup = ordered.withIndex().filter { groupOf(it.value) == group }.map { it.index }
        val position = sameGroup.indexOfFirst { ordered[it] == id }
        val target = sameGroup.getOrNull(position + delta)
        if (position < 0 || target == null) return this
        val from = sameGroup[position]
        val swapped = ordered.toMutableList().also { it[from] = ordered[target]; it[target] = ordered[from] }
        return copy(ordered = swapped)
    }

    /** Vrai si [id] peut monter (delta -1) ou descendre (+1) dans son groupe. */
    fun canMove(id: String, delta: Int, groupOf: (String) -> IndexTable.Group?): Boolean = move(id, delta, groupOf) !== this

    fun toggled(id: String) = copy(disabled = if (id in disabled) disabled - id else disabled + id)
}

object ObjectLayouts {

    fun effective(catalogue: List<String>, layout: ObjectLayout): EffectiveLayout {
        val known = catalogue.toSet()
        val head = layout.order.filter { it in known }.distinct()
        val ordered = head + catalogue.filter { it !in head }
        return EffectiveLayout(ordered, layout.disabled.filter { it in known }.toSet())
    }

    fun effective(kind: LayoutKind, layout: ObjectLayout) = effective(kind.catalogue(), layout)

    /**
     * Validation avant l'envoi (même règle que le serveur) : identifiants du catalogue du genre, sans doublon.
     * L'échec porte le code d'erreur du serveur (`unknown_light`, `unknown_shutter` ou `invalid_layout`).
     */
    fun validate(kind: LayoutKind, order: List<String>, disabled: List<String>): Result<Unit> {
        val known = kind.catalogue().toSet()
        return when {
            (order + disabled).any { it !in known } -> Result.failure(LayoutException(kind.unknownCode))
            order.size != order.distinct().size || disabled.size != disabled.distinct().size ->
                Result.failure(LayoutException(INVALID_CODE))
            else -> Result.success(Unit)
        }
    }

    class LayoutException(val code: String) : IllegalArgumentException(code)

    const val INVALID_CODE = "invalid_layout"
    const val NO_LIGHT = "Aucune lumière activée"
    const val NO_SHUTTER = "Aucun volet activé"

    fun emptyText(kind: LayoutKind) = if (kind == LayoutKind.LIGHTS) NO_LIGHT else NO_SHUTTER

    /** Messages des erreurs 400, 403 et 503 de l'écriture ; les autres gardent le message commun. */
    fun errorMessage(error: ApiError): String = when {
        error is ApiError.Forbidden -> "Réservé à l'administrateur de l'armoire."
        error is ApiError.Http && error.code == "unknown_light" -> "Lumière inconnue : la disposition n'a pas été enregistrée."
        error is ApiError.Http && error.code == "unknown_shutter" -> "Volet inconnu : la disposition n'a pas été enregistrée."
        error is ApiError.Http && error.code == INVALID_CODE -> "Disposition invalide : elle n'a pas été enregistrée."
        error is ApiError.Http && (error.code == "names_unavailable" || error.status == 503) ->
            "Stockage de la disposition indisponible sur la gateway, réessayez plus tard."
        else -> error.userMessage
    }
}
