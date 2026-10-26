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

        /** Temps de course (spec shutter-travel-time) ; null hors de [travelTimeRange]. */
        fun travelTimeCommand(seconds: Int): Injection? =
            if (seconds in travelTimeRange) Injection(travelTimeIndex, seconds.toString()) else null
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

    // --- Chauffage (spec heating-control) : `essensys-user-portal-frontend/src/heating/constants.ts` ---

    /**
     * Valeur d'un créneau horaire du planning (3 bits). Hors gel (5) peut être lu dans l'armoire mais
     * n'est pas proposé à l'édition, comme dans le portail : la grille garde la valeur brute pour ne
     * jamais réécrire un créneau que l'utilisateur n'a pas touché.
     */
    enum class HeatingSlot(val value: Int, val label: String, val inPalette: Boolean = true) {
        OFF(0, "OFF"), CONFORT(1, "Confort"), ECO(2, "Éco"), ECO_PLUS(3, "Éco+"), ECO_PLUS_PLUS(4, "Éco++"),
        FROST(5, "Hors gel", inPalette = false);

        companion object {
            val palette = entries.filter { it.inPalette }
            fun of(value: Int) = entries.firstOrNull { it.value == value }
        }
    }

    data class HeatingZone(
        val id: String,
        val name: String,
        val scheduleStart: Int,
        val modeIndex: Int,
        val allowsAnticipated: Boolean,
    ) {
        val scheduleIndices: IntRange get() = scheduleStart until scheduleStart + SCHEDULE_BYTES
        fun immediateModes() = ImmediateMode.entries.filter { allowsAnticipated || it != ImmediateMode.ANTICIPATED }
        fun command(mode: ImmediateMode): Injection {
            require(mode in immediateModes()) { "${mode.label} non proposé pour $name" }
            return Injection(modeIndex, mode.value.toString())
        }
    }

    /** 7 jours × 12 octets (2 heures par octet). */
    const val SCHEDULE_BYTES = 84
    const val SCHEDULE_BYTES_PER_DAY = 12

    val heatingZones: List<HeatingZone> = listOf(
        HeatingZone("jour", "Zone Jour", scheduleStart = 13, modeIndex = 349, allowsAnticipated = true),
        HeatingZone("nuit", "Zone Nuit", scheduleStart = 97, modeIndex = 350, allowsAnticipated = true),
        HeatingZone("sdb1", "Salle de bain 1", scheduleStart = 181, modeIndex = 351, allowsAnticipated = false),
        HeatingZone("sdb2", "Salle de bain 2", scheduleStart = 265, modeIndex = 352, allowsAnticipated = false),
    )

    /** Mode immédiat (`Chauf_*_Mode`) : b0–b3 consigne, b4–b5 automatique / forcé / anticipé. */
    enum class ImmediateMode(val value: Int, val label: String) {
        AUTO(1, "Automatique (planning)"),
        ANTICIPATED(32, "Anticipé"),
        FORCE_CONFORT(17, "Forçage confort"),
        FORCE_ECO(18, "Forçage éco"),
        FORCE_ECO_PLUS(19, "Forçage éco+"),
        FORCE_ECO_PLUS_PLUS(20, "Forçage éco++"),
        FROST(21, "Forçage hors gel"),
        OFF(16, "OFF"),
    }

    // --- Chauffe-eau et arrosage (spec comfort-controls) : indices du firmware `TableEchange.h` ---
    // Le portail envoie 400–402 et 500–504 (absents du firmware) : NE PAS les reprendre (design D3).

    const val WATER_HEATER_INDEX = 353 // Cumulus_Mode
    const val SPRINKLER_INDEX = 363 // Arrose_Mode

    enum class WaterHeaterMode(val value: Int, val label: String) {
        AUTONOMOUS(0, "Autonome (marche permanente)"),
        OFF_PEAK(1, "Heures creuses"),
        OFF(2, "Arrêt");

        fun command() = Injection(WATER_HEATER_INDEX, value.toString())
    }

    enum class SprinklerMode(val value: Int, val label: String) {
        AUTO(255, "Automatique"),
        MINUTES_15(15, "15 minutes"),
        MINUTES_30(30, "30 minutes"),
        MINUTES_60(60, "60 minutes"),
        OFF(0, "Arrêt");

        fun command() = Injection(SPRINKLER_INDEX, value.toString())
    }

    // --- Scénarios (spec scenarios) : `essensys-user-portal-backend/internal/domain/scenario/indices.go` ---

    const val SCENARIO_FIRST_BASE = 592
    const val SCENARIO_SIZE = 41
    val scenarioSlots = 2..8
    val restorableScenarioSlots = 2..6

    fun scenarioBlock(slot: Int): IntRange {
        val base = SCENARIO_FIRST_BASE + (slot - 1) * SCENARIO_SIZE
        return base until base + SCENARIO_SIZE
    }

    /**
     * Les métadonnées `meta/bitmasks` décrivent les indices du scénario 1 (605, 606…) : on les
     * transpose dans le bloc du scénario édité (design D6). Un indice déjà dans le bloc est conservé.
     */
    fun shiftIntoScenario(metaIndex: Int, slot: Int): Int {
        val block = scenarioBlock(slot)
        if (metaIndex in block) return metaIndex
        val offset = metaIndex - SCENARIO_FIRST_BASE
        require(offset in 0 until SCENARIO_SIZE) { "indice $metaIndex hors du bloc du scénario 1" }
        return block.first + offset
    }

    // --- Prises de sécurité (spec security-outlets) : indices du firmware `TableEchange.h` ---
    // Commande : Scenario_Securite du scénario 1 (592 + 31), déclenché par 590 = 1. Verrouillé par NR-android-18.

    const val SECURITY_OUTLETS_COMMAND_INDEX = 623 // Scenario_Securite, scénario 1
    const val SECURITY_OUTLETS_STATE_INDEX = 440 // Securite_PriseCoupe : 1 = coupées, 0 = pilotées
    const val SCENARIO_SECURITY_OFFSET = 31

    enum class SecurityOutletsAction(val value: Int, val label: String) {
        CUT(1, "Coupure des prises de sécurité"),
        RESTORE(2, "Rétablissement des prises de sécurité");

        /** Un seul lot : déclenchement du scénario 1 et commande. */
        fun commands() = listOf(Injection(590, "1"), Injection(SECURITY_OUTLETS_COMMAND_INDEX, value.toString()))

        val expectedState: SecurityOutletsState get() = if (this == CUT) SecurityOutletsState.CUT else SecurityOutletsState.PILOTED
    }

    enum class SecurityOutletsState(val label: String) {
        CUT("Prises coupées"), PILOTED("Prises pilotées"), UNKNOWN("État inconnu");

        companion object {
            fun of(raw: String?) = when (raw?.trim()) {
                "1" -> CUT
                "0" -> PILOTED
                else -> UNKNOWN
            }
        }
    }

    /** Choix « Prises de sécurité » d'un scénario (valeur de Scenario_Securite). */
    enum class ScenarioSecurity(val value: Int, val label: String) {
        NOTHING(0, "Ne rien faire"), CUT(1, "Couper"), RESTORE(2, "Rétablir");

        companion object {
            fun of(value: Int?) = entries.firstOrNull { it.value == value } ?: NOTHING
        }
    }

    fun scenarioSecurityIndex(slot: Int): Int = scenarioBlock(slot).first + SCENARIO_SECURITY_OFFSET

    /** Limite firmware : 30 paramètres au plus par action (`heating/injectLimits.ts`). */
    const val MAX_PARAMS_PER_ACTION = 30

    fun <T> chunk(params: List<T>, size: Int = MAX_PARAMS_PER_ACTION): List<List<T>> = params.chunked(size)
}

/** Une écriture dans la table d'échange : `{"k": <int>, "v": "<string>"}`. */
data class Injection(val k: Int, val v: String)
