package com.essensys.android.ui.screens

/** Onglets (spec home-dashboard « Navigation ») ; les autres écrans s'ouvrent depuis l'accueil. */
enum class Destination(val label: String, val title: String) {
    HOME("Accueil", "Essensys"),
    LIGHTING("Éclairage", "Éclairage"),
    SHUTTERS("Volets", "Volets & stores"),
    HEATING("Chauffage", "Chauffage"),
    SETTINGS("Réglages", "Réglages"),
}
