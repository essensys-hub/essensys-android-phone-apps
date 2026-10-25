## Purpose

Permet de consulter et de modifier depuis l'app le planning hebdomadaire de chauffage des 4 zones et leur mode immédiat, avec exactement les mêmes octets de la table d'échange que le portail.

## ADDED Requirements

### Requirement: Zones et indices
L'app SHALL proposer les 4 zones du portail avec leurs indices : Zone Jour (planning 13–96, mode 349), Zone Nuit (97–180, 350), Salle de bain 1 (181–264, 351), Salle de bain 2 (265–348, 352). Chaque planning occupe 84 octets (7 jours × 12 octets). Ces indices SHALL être définis dans la table unique de l'app et verrouillés par des tests de non-régression.

#### Scenario: Ouvrir la zone nuit
- **WHEN** l'utilisateur sélectionne « Zone Nuit »
- **THEN** l'app lit les indices 97 à 180 et affiche le planning correspondant

### Requirement: Codage du planning
Un octet SHALL coder deux heures consécutives : l'heure paire dans les bits 0–2 et l'heure impaire dans les bits 4–6. L'octet d'un créneau SHALL être à l'indice `début_zone + jour × 12 + heure ÷ 2` (lundi = jour 0). Les valeurs SHALL être OFF = 0, Confort = 1, Éco = 2, Éco+ = 3, Éco++ = 4. Un octet absent de la lecture SHALL être affiché OFF.

#### Scenario: Codage lundi 6 h–8 h
- **WHEN** l'utilisateur met lundi 6 h en Confort et lundi 7 h en Éco dans la Zone Jour, le reste de l'octet étant inchangé
- **THEN** l'octet d'indice 16 envoyé vaut `0x21` (33)

### Requirement: Lecture du planning
En cloud, l'app SHALL lire le planning par `GET /api/portal/exchange?keys=…` et SHALL signaler à l'utilisateur une réponse marquée `stale` ou de source `none` (« Données de l'armoire peut-être anciennes »). En LAN, elle SHALL lire par `GET /api/admin/exchange?keys=…`.

#### Scenario: Cache ancien en cloud
- **WHEN** la réponse cloud porte `stale: true`
- **THEN** le planning est affiché avec un avertissement visible

### Requirement: Édition et envoi par lots
L'utilisateur SHALL pouvoir modifier des créneaux, appliquer un jour à toute la semaine, annuler ses modifications et enregistrer. À l'enregistrement, l'app SHALL envoyer uniquement les octets modifiés, découpés en lots de 30 couples au plus, envoyés l'un après l'autre (cloud : `POST /api/portal/inject/batch` `{params:[{k,v}]}` ; LAN : `POST /api/admin/inject` avec un tableau). L'app SHALL afficher le nombre d'octets et de lots avant l'envoi, puis la progression lot par lot. Si un lot échoue, l'app SHALL arrêter l'envoi, indiquer les lots déjà envoyés et garder les modifications non envoyées.

#### Scenario: Enregistrer 40 octets modifiés
- **WHEN** l'utilisateur enregistre un planning dont 40 octets ont changé
- **THEN** l'app envoie deux lots (30 puis 10 couples), dans cet ordre, et affiche « 2 envois armoire »

#### Scenario: Échec du second lot
- **WHEN** le second lot reçoit une erreur 429
- **THEN** l'app affiche « Trop de commandes, patientez », indique que le premier lot est envoyé et propose de renvoyer le reste

### Requirement: Synchronisation depuis l'armoire
En LAN, « Synchroniser avec l'armoire » SHALL lancer `POST /api/admin/heating/sync` pour la zone, interroger `GET /api/admin/heating/sync/status` toutes les 3 s pendant 90 s au plus en affichant la progression, puis relire le planning. Une réponse 409 `busy` SHALL être affichée « Synchronisation déjà en cours ». En cloud, l'action SHALL seulement relire le cache et le dire (« Dernières valeurs reçues de l'armoire »).

#### Scenario: Synchronisation LAN
- **WHEN** l'utilisateur synchronise la Zone Jour en LAN
- **THEN** l'app démarre la synchronisation des octets 13 à 96, affiche la progression puis le planning relu

### Requirement: Mode immédiat
Pour chaque zone, l'app SHALL proposer les modes du portail et envoyer une injection unique sur l'indice de mode de la zone : Automatique = 1, Anticipé = 32 (zones Jour et Nuit seulement), Forçage confort = 17, éco = 18, éco+ = 19, éco++ = 20, Hors gel = 21, OFF = 16.

#### Scenario: Forcer la salle de bain 1 en confort
- **WHEN** l'utilisateur choisit « Forçage confort » pour la salle de bain 1
- **THEN** l'app envoie l'injection `k=351, v="17"`

#### Scenario: Anticipé absent des salles de bain
- **WHEN** l'utilisateur ouvre le mode immédiat de la salle de bain 2
- **THEN** le mode « Anticipé » n'est pas proposé
