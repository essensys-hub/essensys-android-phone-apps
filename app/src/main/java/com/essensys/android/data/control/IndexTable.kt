package com.essensys.android.data.control

/**
 * Référentiel unique des commandes Éclairage / Volets (spec domotic-controls, design D5).
 *
 * Copie fidèle de `essensys-user-portal-frontend/src/pages/LightingPage.tsx` et `ShuttersPage.tsx`
 * (le code du portail fait foi). Indices legacy de la table d'échange : NE PAS les modifier ici sans
 * modifier le portail et la mémoire (essensys-memory). Verrouillé par les tests NR-android-5 / NR-android-6.
 * Valeur `v` envoyée = [mask] en chaîne (masque binaire de la sortie).
 */
object IndexTable {

    enum class Group { MAIN, INDIRECT, SPECIAL }

    data class Light(
        val id: String,
        val name: String,
        val group: Group,
        val mask: Int,
        val onIndex: Int,
        val offIndex: Int,
    ) {
        fun command(on: Boolean) = Injection(if (on) onIndex else offIndex, mask.toString())
    }

    data class Shutter(
        val id: String,
        val name: String,
        val group: Group,
        val mask: Int,
        val openIndex: Int,
        val closeIndex: Int,
        val travelTimeIndex: Int,
    ) {
        fun command(open: Boolean) = Injection(if (open) openIndex else closeIndex, mask.toString())
    }

    val lights: List<Light> = listOf(
        Light("terrasse", "Terrasse", Group.MAIN, mask = 4, onIndex = 616, offIndex = 610),
        Light("entree", "Entrée", Group.MAIN, mask = 1, onIndex = 611, offIndex = 605),
        Light("escalier", "Escalier", Group.MAIN, mask = 1, onIndex = 613, offIndex = 607),
        Light("deg1", "Dégagement 1", Group.MAIN, mask = 1, onIndex = 616, offIndex = 610),
        Light("deg2", "Dégagement 2", Group.MAIN, mask = 2, onIndex = 616, offIndex = 610),
        Light("pieceserv", "Pièce de service", Group.MAIN, mask = 128, onIndex = 615, offIndex = 609),
        Light("ann1", "Annexe 1", Group.MAIN, mask = 8, onIndex = 616, offIndex = 610),
        Light("ann2", "Annexe 2", Group.MAIN, mask = 16, onIndex = 616, offIndex = 610),
        Light("salon", "Salon", Group.MAIN, mask = 128, onIndex = 612, offIndex = 606),
        Light("sam", "Salle à Manger", Group.MAIN, mask = 64, onIndex = 612, offIndex = 606),
        Light("cuisine", "Cuisine", Group.MAIN, mask = 1, onIndex = 615, offIndex = 609),
        Light("sdb1", "Salle de Bain 1", Group.MAIN, mask = 128, onIndex = 616, offIndex = 610),
        Light("sdb2", "Salle de Bain 2", Group.MAIN, mask = 8, onIndex = 615, offIndex = 609),
        Light("wc1", "WC 1", Group.MAIN, mask = 32, onIndex = 615, offIndex = 609),
        Light("wc2", "WC 2", Group.MAIN, mask = 64, onIndex = 615, offIndex = 609),
        Light("bureau", "Bureau", Group.MAIN, mask = 32, onIndex = 612, offIndex = 606),
        Light("gdchamb", "Grande Chambre", Group.MAIN, mask = 128, onIndex = 614, offIndex = 608),
        Light("ptchamb1", "Petite Chambre 1", Group.MAIN, mask = 64, onIndex = 614, offIndex = 608),
        Light("ptchamb2", "Petite Chambre 2", Group.MAIN, mask = 32, onIndex = 614, offIndex = 608),
        Light("ptchamb3", "Petite Chambre 3", Group.MAIN, mask = 16, onIndex = 614, offIndex = 608),
        Light("dressing", "Dressing", Group.MAIN, mask = 8, onIndex = 611, offIndex = 605),
        Light("isalonind", "Salon (indirect 1)", Group.INDIRECT, mask = 2, onIndex = 611, offIndex = 605),
        Light("isalonind2", "Salon (indirect 2)", Group.INDIRECT, mask = 4, onIndex = 611, offIndex = 605),
        Light("icuisine", "Cuisine (plans de travail)", Group.INDIRECT, mask = 2, onIndex = 615, offIndex = 609),
        Light("isdb1", "Salle de Bain 1 (miroir)", Group.INDIRECT, mask = 4, onIndex = 615, offIndex = 609),
        Light("isdb2", "Salle de Bain 2 (miroir)", Group.INDIRECT, mask = 16, onIndex = 615, offIndex = 609),
        Light("igdchamb1", "Grande Chambre (chevet 1)", Group.INDIRECT, mask = 2, onIndex = 613, offIndex = 607),
        Light("igdchamb2", "Grande Chambre (chevet 2)", Group.INDIRECT, mask = 4, onIndex = 613, offIndex = 607),
        Light("iptchamb1", "Petite Chambre 1 (chevet 1)", Group.INDIRECT, mask = 8, onIndex = 613, offIndex = 607),
        Light("iptchamb2", "Petite Chambre 1 (chevet 2)", Group.INDIRECT, mask = 16, onIndex = 613, offIndex = 607),
        Light("iptchamb22", "Petite Chambre 2 (chevet)", Group.INDIRECT, mask = 32, onIndex = 613, offIndex = 607),
        Light("iptchamb3", "Petite Chambre 3 (chevet)", Group.INDIRECT, mask = 64, onIndex = 613, offIndex = 607),
        Light("idressing", "Dressing (placards)", Group.INDIRECT, mask = 16, onIndex = 611, offIndex = 605),
    )

    val shutters: List<Shutter> = listOf(
        Shutter("volet1salon", "Volet 1 Salon", Group.MAIN, mask = 1, openIndex = 617, closeIndex = 620, travelTimeIndex = 566),
        Shutter("volet2salon", "Volet 2 Salon", Group.MAIN, mask = 2, openIndex = 617, closeIndex = 620, travelTimeIndex = 567),
        Shutter("volet3salon", "Volet 3 Salon", Group.MAIN, mask = 4, openIndex = 617, closeIndex = 620, travelTimeIndex = 568),
        Shutter("volet1salleamanger", "Volet 1 Salle à Manger", Group.MAIN, mask = 8, openIndex = 617, closeIndex = 620, travelTimeIndex = 569),
        Shutter("volet2salleamanger", "Volet 2 Salle à Manger", Group.MAIN, mask = 16, openIndex = 617, closeIndex = 620, travelTimeIndex = 570),
        Shutter("volet1cuisine", "Volet 1 Cuisine", Group.MAIN, mask = 1, openIndex = 619, closeIndex = 622, travelTimeIndex = 582),
        Shutter("volet2cuisine", "Volet 2 Cuisine", Group.MAIN, mask = 2, openIndex = 619, closeIndex = 622, travelTimeIndex = 583),
        Shutter("voletsdb", "Volet Salle de Bain 1", Group.MAIN, mask = 4, openIndex = 619, closeIndex = 622, travelTimeIndex = 584),
        Shutter("volet1gdchamb", "Volet 1 Grande Chambre", Group.MAIN, mask = 1, openIndex = 618, closeIndex = 621, travelTimeIndex = 574),
        Shutter("volet2gdchamb", "Volet 2 Grande Chambre", Group.MAIN, mask = 2, openIndex = 618, closeIndex = 621, travelTimeIndex = 575),
        Shutter("volet1ptchamb", "Volet Petite Chambre 1", Group.MAIN, mask = 4, openIndex = 618, closeIndex = 621, travelTimeIndex = 576),
        Shutter("volet2ptchamb", "Volet Petite Chambre 2", Group.MAIN, mask = 8, openIndex = 618, closeIndex = 621, travelTimeIndex = 577),
        Shutter("volet3ptchamb", "Volet Petite Chambre 3", Group.MAIN, mask = 16, openIndex = 618, closeIndex = 621, travelTimeIndex = 578),
        Shutter("voletbureau", "Volet Bureau", Group.MAIN, mask = 32, openIndex = 617, closeIndex = 620, travelTimeIndex = 571),
        Shutter("voletstore", "Volet \"Store\"", Group.SPECIAL, mask = 64, openIndex = 617, closeIndex = 620, travelTimeIndex = 572),
        Shutter("store", "Store (banne)", Group.SPECIAL, mask = 8, openIndex = 619, closeIndex = 622, travelTimeIndex = 585),
    )

    /** Temps de course : indices 566–589, valeur 1–255 secondes. */
    val travelTimeRange = 1..255
}

/** Une écriture dans la table d'échange : `{"k": <int>, "v": "<string>"}`. */
data class Injection(val k: Int, val v: String)
