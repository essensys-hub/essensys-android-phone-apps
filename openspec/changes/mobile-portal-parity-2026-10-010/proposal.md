> Ticket : [essensys-hub/essensys-feature-lifecycle#35](https://github.com/essensys-hub/essensys-feature-lifecycle/issues/35). Feature ID : `mobile-portal-parity-2026-10-010`.
> Feature commune aux deux apps : ce change (Android, référence) a un jumeau du même nom dans `essensys-ios-phone-apps`. Les specs fonctionnelles sont identiques ; seuls le design et la spec qualité sont propres à chaque plateforme.
> Suite de `android-portal-refresh-2026-10-002` (v2.0.0, #11).

## Why

L'app v2.0.0 ne couvre que l'éclairage et les volets. Le chauffage, les scénarios, le chauffe-eau, l'arrosage et l'alarme sont affichés « Bientôt disponible », et l'utilisateur doit ouvrir le portail web pour s'en servir. La v2.1.0 donne aux deux apps toutes les fonctions du portail utiles à un occupant, avec les mêmes commandes sur Android et sur iPhone.

## What Changes

- **Chauffage** : planning hebdomadaire des 4 zones (jour, nuit, salle de bain 1 et 2) en lecture et en écriture, et mode immédiat par zone (automatique, anticipé, forçages, hors gel, OFF). Même codec et mêmes limites d'envoi (30 octets par action) que le portail. En LAN, synchronisation réelle depuis l'armoire.
- **Scénarios** : lancement des scénarios 2 à 8 (Je sors, vacances, Je rentre, coucher, lever, personnalisés 1 et 2), retour aux réglages d'usine (2 à 6), édition des scénarios modifiables. « Je sors » remplace le bouton « Départ » de l'app iPhone v1.
- **Chauffe-eau** : modes autonome, heures creuses et OFF.
- **Arrosage** : automatique, forcé 15, 30 ou 60 minutes, OFF.
- **Alarme** : mise en service et arrêt avec le code à 4 chiffres, en cloud seulement. Le code n'est jamais stocké ni journalisé par l'app.
- **Temps de course des volets** (ajout du 2026-10-25) : réglage de 1 à 255 secondes par volet et store, comme la carte « Temps de course » du portail ; la v2.0.0 ne faisait que l'afficher.
- **Accueil** : la carte « Bientôt disponible » est remplacée par des raccourcis vers toutes les fonctions et par les scénarios rapides. La navigation passe de 4 à 5 onglets (Chauffage ajouté).
- **Indices du firmware, pas du portail**, pour le chauffe-eau et l'arrosage : le portail envoie les indices 400–402 et 500–504, qui n'existent pas dans `TableEchange.h`. Les apps envoient `Cumulus_Mode` (353, valeurs 0/1/2) et `Arrose_Mode` (363, valeurs 0, 1–254 minutes, 255 automatique). Le défaut du portail fait l'objet d'un ticket séparé.
- **Hors périmètre** : notifications SMS et email (page factice dans le portail, aucun backend), UniFi Protect (LAN seulement, sans route dans le portail cloud), écrans d'administration et de supervision, date de fin de vacances, widgets, push, Siri et raccourcis système.
- Version 2.1.0 sur les deux apps.

## Capabilities

### New Capabilities

- `heating-control` : lecture et écriture des plannings de chauffage par zone, mode immédiat, synchronisation LAN, envoi par lots.
- `scenarios` : liste, lancement, retour aux réglages d'usine et édition des scénarios 2 à 8.
- `comfort-controls` : modes du chauffe-eau et de l'arrosage.
- `alarm-control` : mise en service et arrêt de l'alarme par code, traitement du code.
- `shutter-travel-time` : réglage du temps de course de chaque volet (indices 566–589), plage 1–255 s, valeur actuelle.
- `home-dashboard` : accueil, raccourcis, scénarios rapides et navigation à 5 onglets.
- `parity-quality` : tests unitaires, tests applicatifs, non-régression `NR-android-*` alignés sur l'iPhone, garde no-armoire, checkup et livraison 2.1.0.

### Modified Capabilities

Aucune. Les capabilities de la v2.0.0 (`domotic-controls`, `connection-modes`, etc.) sont encore dans le change `android-portal-refresh-2026-10-002`, non archivé. L'accueil V1 qu'il décrit est remplacé par `home-dashboard`.

## Impact

- **Code** : `data/control` (`IndexTable` étendue, nouveaux repositories), `ui/screens` (Chauffage, Scénarios, Chauffe-eau, Arrosage, Alarme, Accueil), `ui/AppRoot` (navigation), fixtures et faux backend de test.
- **API** (aucune modification de backend) :
  - cloud : `/api/portal/exchange`, `/api/portal/inject`, `/api/portal/inject/batch`, `/api/portal/scenarios/*`, `/api/portal/web/actions` ;
  - LAN : `/api/admin/exchange`, `/api/admin/inject`, `/api/admin/heating/sync*`, `/api/scenarios/*`.
- **Dépendances** : aucune nouvelle.
- **Tickets séparés à ouvrir** (constatés pendant l'analyse, hors de ce change) :
  - portail : indices du chauffe-eau et de l'arrosage ;
  - portail : onglet « Lumières & volets » de l'éditeur de scénarios (indices absolus du slot 1) ;
  - backends : sécurité du code d'alarme (stocké en clair dans `cloud_actions`, journalisé en LAN, pas de limite de débit en cloud), route `/api/web/actions` absente quand l'IAM LAN est actif, encodage du code (le firmware attend un chiffre par quartet).
