package com.essensys.android.ui.screens

/** Onglets (spec home-dashboard « Navigation ») ; les autres écrans s'ouvrent depuis l'accueil. */
enum class Destination(val label: String, val title: String) {
    HOME("Accueil", "Essensys"),
    LIGHTING("Éclairage", "Éclairage"),
    SHUTTERS("Volets", "Volets & stores"),
    HEATING("Chauffage", "Chauffage"),
    SETTINGS("Réglages", "Réglages"),
}

/** Écrans poussés depuis l'accueil, avec retour vers l'onglet courant (spec home-dashboard). */
enum class Feature(val title: String) {
    SCENARIOS("Scénarios"),
    WATER_HEATER("Chauffe-eau"),
    SPRINKLER("Arrosage"),
    ALARM("Alarme"),
}
