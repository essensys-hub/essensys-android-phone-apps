package com.essensys.android.ui.screens

/** Navigation V1 (spec : Éclairage, Volets, Réglages ; le reste en V2). */
enum class Destination(val label: String, val title: String) {
    HOME("Accueil", "Essensys"),
    LIGHTING("Éclairage", "Éclairage"),
    SHUTTERS("Volets", "Volets & stores"),
    SETTINGS("Réglages", "Réglages"),
}
