## 1. Socle data et tests (#11)

- [ ] 1.1 Étendre `IndexTable` (zones de chauffage, modes immédiats, chauffe-eau 353, arrosage 363, formule de bloc des scénarios) ; vérifier par `IndexTableTest` et les tests `NR-android-10` (indices des zones) et `NR-android-11` (chauffe-eau et arrosage, aucun indice 400–402 ou 500–504)
- [ ] 1.2 Ajouter au client HTTP la lecture `exchange` (cloud avec `stale`/`source`, LAN) et l'envoi par lots (cloud `inject/batch`, LAN `inject` en tableau) avec découpage à 30 ; vérifier par des tests `MockWebServer` (découpage 40 → 30 + 10 = `NR-android-12`)
- [ ] 1.3 Étendre `NoArmoireInterceptor` aux routes `inject/batch`, `web/actions`, `scenarios/*` et `heating/sync` ; vérifier par un test qui échoue sur chaque route vers `mon.essensys.fr`
- [ ] 1.4 Étendre le faux backend instrumenté et les fixtures JSON (exchange, scenarios, bitmasks, test_ok, 409 busy, 429) ; vérifier qu'un test instrumenté existant passe toujours

## 2. Chauffage (#12)

- [ ] 2.1 `ScheduleCodec` (décodage, encodage, diff, copie d'un jour) porté de `scheduleCodec.ts` ; vérifier par `NR-android-13` (lundi 6 h Confort + 7 h Éco en Zone Jour → `k=16, v="33"`) et des tests de diff
- [ ] 2.2 `HeatingRepository` cloud et LAN : lecture du planning, envoi séquentiel interruptible avec événements de progression, synchronisation LAN (`heating/sync`, statut toutes les 3 s, 90 s, 409 busy) ; vérifier par des tests unitaires dont l'échec du second lot
- [ ] 2.3 Écran Chauffage : onglets de zones, grille 7 × 24 avec palette, « appliquer à toute la semaine », Annuler, Enregistrer avec compteur d'octets et de lots, progression, avertissement `stale` ; vérifier par un test instrumenté (modifier, enregistrer, lot reçu par le faux backend) et à 360 dp
- [ ] 2.4 Mode immédiat par zone (Anticipé absent des salles de bain) ; vérifier par `NR-android-14` (`k=351, v="17"` pour forçage confort SdB1) et un test instrumenté

## 3. Scénarios (#13)

- [ ] 3.1 `ScenarioRepository` cloud et LAN : liste, définition, bitmasks, lancement, restauration, enregistrement, en-tête dry-run partout ; vérifier par des tests unitaires (dont dry-run en LAN)
- [ ] 3.2 Transposition des masques dans le bloc du scénario et refus local des indices hors bloc ; vérifier par `NR-android-15` (indice méta 605 dans le scénario 4 → 728)
- [ ] 3.3 Écran Scénarios : liste 2 à 8, dernier lancé, Lancer, Réglages d'usine avec confirmation (2 à 6), éditeur à cases à cocher ; vérifier par des tests instrumentés (annuler la confirmation n'envoie rien)

## 4. Chauffe-eau, arrosage et alarme (#14)

- [ ] 4.1 Écrans Chauffe-eau et Arrosage avec mode actuel lu dans `exchange` ou « Mode actuel inconnu » ; vérifier par des tests instrumentés (`k=353, v="1"`, `k=363, v="30"`)
- [ ] 4.2 Écran Alarme cloud (code à 4 chiffres masqué, confirmation de l'arrêt, rappel de boucle ouverte) et message « cloud seulement » en LAN ; vérifier par des tests instrumentés en cloud et en LAN
- [ ] 4.3 Protection du code : pas de stockage ni d'état restauré, effacement après envoi et à `ON_STOP`, corps masqué dans les journaux, `validated_params` non affiché ; vérifier par `NR-android-16` (code absent des journaux et du stockage)
- [ ] 4.4 Masquer l'écran Alarme en release derrière un drapeau tant que la mise en service n'est pas validée par un humain sur le banc BP ; vérifier que la build release n'affiche pas la tuile Alarme

## 5. Accueil et navigation (#15)

- [ ] 5.1 Navigation à 5 onglets (Accueil, Éclairage, Volets, Chauffage, Réglages) et écrans poussés depuis l'accueil ; vérifier par un test instrumenté de navigation
- [ ] 5.2 Accueil : scénarios rapides, tuiles de toutes les fonctions, suppression de « Bientôt disponible », commandes désactivées hors ligne ; vérifier par des tests instrumentés (hors ligne : « Je sors » désactivé, planning lisible)
- [ ] 5.3 Réglages : compte et armoire lus dans `/api/portal/session` en cloud ; vérifier par un test instrumenté

## 6. Qualité, CI et livraison (#16)

- [ ] 6.1 CI verte sur la PR (build, lint, unitaires, NR) et rapport NR consolidé avec `NR-android-10` à `NR-android-16` ; vérifier sur le run GitHub
- [ ] 6.2 `/checkup essensys-android-phone-apps` vert, captures clair et sombre des nouveaux écrans postées sur #35
- [ ] 6.3 README (fonctionnalités, captures) mis à jour et `versionName` 2.1.0 ; vérifier le rendu du README
- [ ] 6.4 Release `android-v2.1.0` signée par `release.yml` ; vérifier la release GitHub et son SHA-256
