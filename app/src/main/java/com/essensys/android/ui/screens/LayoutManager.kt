package com.essensys.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.essensys.android.data.control.IndexTable
import com.essensys.android.data.control.LayoutKind
import com.essensys.android.ui.components.ActionButton
import com.essensys.android.ui.components.EssensysCard
import com.essensys.android.ui.components.FeedbackBanner
import com.essensys.android.ui.components.Tone
import com.essensys.android.ui.theme.Essensys

/**
 * Écran de gestion de l'administrateur de l'armoire (spec object-layout-clients) : tous les objets du genre,
 * désactivés compris, dans l'ordre effectif et par groupe, avec interrupteur Activé/Désactivé et Monter/Descendre.
 * Les modifications restent dans le brouillon du [vm] jusqu'à « Enregistrer » (une seule écriture). Les noms
 * affichés ([nameOf]) sont du texte brut. Ne commande jamais l'armoire.
 */
@Composable
fun LayoutManager(
    kind: LayoutKind,
    vm: ObjectLayoutViewModel,
    groups: List<Pair<IndexTable.Group, String>>,
    nameOf: (String) -> String,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val tag = "layout-${kind.path}"
    val draft = state.draft
    FeedbackBanner(state.feedback)
    if (draft == null) {
        TextButton({ vm.startEdit() }, Modifier.testTag("$tag-manage")) {
            Text(if (kind == LayoutKind.LIGHTS) "Gérer l'affichage des lumières  ▾" else "Gérer l'affichage des volets  ▾")
        }
        return
    }
    EssensysCard(
        title = if (kind == LayoutKind.LIGHTS) "Affichage des lumières" else "Affichage des volets",
        description = "Activer, désactiver et ordonner. Les modifications s'appliquent à tous les utilisateurs après « Enregistrer ».",
        modifier = Modifier.testTag("$tag-card"),
    ) {
        groups.forEach { (group, title) ->
            val ids = draft.layout.ordered.filter { kind.groupOf(it) == group }
            Text(title, Modifier.testTag("$tag-group-$group"), style = MaterialTheme.typography.titleSmall, color = Essensys.colors.text)
            ids.forEach { id ->
                val enabled = draft.layout.isEnabled(id)
                Row(Modifier.fillMaxWidth().testTag("$tag-row-$id"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(nameOf(id), Modifier.testTag("$tag-name-$id"), style = MaterialTheme.typography.bodyMedium, color = Essensys.colors.text)
                        Text(
                            if (enabled) "Activé" else "Désactivé", Modifier.testTag("$tag-state-$id"),
                            style = MaterialTheme.typography.bodySmall, color = if (enabled) Essensys.colors.textMuted else Essensys.colors.warning,
                        )
                    }
                    Switch(enabled, { vm.toggle(id) }, Modifier.testTag("$tag-switch-$id").semantics { contentDescription = "Activé" })
                    ActionButton(
                        "▲", { vm.move(id, -1) }, Modifier.testTag("$tag-up-$id").semantics { contentDescription = "Monter" },
                        Tone.SECONDARY, enabled = draft.layout.canMove(id, -1, kind::groupOf) && !state.saving, compact = true,
                    )
                    ActionButton(
                        "▼", { vm.move(id, 1) }, Modifier.testTag("$tag-down-$id").semantics { contentDescription = "Descendre" },
                        Tone.SECONDARY, enabled = draft.layout.canMove(id, 1, kind::groupOf) && !state.saving, compact = true,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("Ordre d'origine", { vm.resetToOrigin() }, Modifier.weight(1f).testTag("$tag-reset"), Tone.SECONDARY, enabled = !state.saving, compact = true)
            ActionButton("Enregistrer", { vm.save() }, Modifier.weight(1f).testTag("$tag-save"), Tone.PRIMARY, loading = state.saving, enabled = !state.saving, compact = true)
        }
        TextButton({ vm.cancelEdit() }, Modifier.testTag("$tag-cancel")) { Text("Fermer sans enregistrer") }
    }
}
