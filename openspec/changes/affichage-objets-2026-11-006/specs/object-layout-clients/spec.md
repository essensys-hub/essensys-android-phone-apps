## Purpose

Appliquer partout la disposition des lumières et des volets (objets désactivés masqués, ordre choisi) et permettre à l'administrateur de l'armoire de la modifier, sur le portail cloud, le portail local, l'app Android et l'app iPhone.

## ADDED Requirements

### Requirement: Lecture et repli
Chaque client SHALL lire les dispositions (`GET /api/portal/{lights|shutters}/layout` en mode cloud, `GET /api/{lights|shutters}/layout` en mode local) et calculer la liste effective avec la règle commune : `order` d'abord, puis les identifiants connus absents dans l'ordre du catalogue ; les identifiants inconnus sont ignorés. Si la lecture échoue ou si le serveur répond 404, le client MUST afficher le catalogue complet dans l'ordre d'origine, sans message bloquant. Après une lecture réussie, une coupure passagère conserve la dernière disposition lue.

#### Scenario: Ancien serveur
- **GIVEN** une gateway ou un cloud qui répond 404
- **WHEN** l'utilisateur ouvre l'écran Éclairage ou Volets
- **THEN** les 33 lumières et les 16 volets et stores sont affichés dans l'ordre d'origine

#### Scenario: Ordre incomplet ou identifiant ancien
- **GIVEN** un `order` qui ne contient que `salon` et un identifiant inexistant
- **THEN** `salon` vient en premier de son groupe, les autres suivent dans l'ordre du catalogue, l'identifiant inexistant est ignoré

### Requirement: Objet désactivé masqué partout
Un objet désactivé SHALL être masqué pour tous les utilisateurs dans les listes (lumières principales, indirectes, volets, store et éléments spéciaux), dans les commandes de groupe (« Tout allumer / éteindre », « Tout ouvrir / fermer » n'incluent pas les objets désactivés), dans l'écran Temporisations et dans l'écran « Temps de course ». Aucune commande, aucun réglage de temporisation ni aucun temps de course MUST être envoyé pour cet objet depuis les clients. Un groupe sans objet actif n'affiche ni boutons de groupe ni ligne, seulement un texte « Aucune lumière activée » ou « Aucun volet activé ». Les scénarios et l'éditeur de scénarios restent sur le catalogue complet.

#### Scenario: Lumière désactivée
- **GIVEN** `wc2` désactivée
- **WHEN** l'utilisateur ouvre l'écran Éclairage puis Temporisations
- **THEN** « WC 2 » n'apparaît dans aucune des deux

#### Scenario: Commande de groupe
- **GIVEN** `wc2` désactivée
- **WHEN** l'utilisateur appuie sur « Tout allumer » du groupe principal
- **THEN** la requête contient les commandes des 20 autres lumières et aucune pour `wc2`

#### Scenario: Volet désactivé
- **GIVEN** `voletbureau` désactivé
- **THEN** il disparaît de la liste des volets, de « Tout ouvrir / fermer » et de la carte « Temps de course »

#### Scenario: Tout désactivé
- **GIVEN** toutes les lumières principales désactivées
- **THEN** le groupe affiche « Aucune lumière activée » et aucun bouton « Tout allumer »

#### Scenario: Scénarios
- **GIVEN** une lumière désactivée
- **THEN** l'éditeur de scénarios la propose toujours

### Requirement: Ordre appliqué dans chaque groupe
L'ordre de `order` SHALL s'appliquer à l'intérieur de chaque groupe d'affichage (principal, indirect, volets, éléments spéciaux) ; un objet ne change jamais de groupe. Les écrans sans groupe (Temporisations, « Temps de course ») suivent l'ordre global filtré. Les noms personnalisés (#41, #52) et les temporisations (#45) restent inchangés.

#### Scenario: Ordre choisi
- **GIVEN** `volet2salon` placé avant `volet1salon`
- **THEN** la liste des volets, la carte « Temps de course » et les commandes de groupe suivent cet ordre

#### Scenario: Noms inchangés
- **GIVEN** `dressing` renommé « WC 3 » et réordonné
- **THEN** il apparaît sous le nom « WC 3 » à sa nouvelle position

### Requirement: Écran de gestion de l'administrateur
Les administrateurs de l'armoire (cloud : `admin_local` ou `admin_global` avec armoire liée ; local : `lan_admin`, ou tous si l'IAM LAN est désactivé) SHALL disposer, depuis les écrans Éclairage et Volets, d'un écran de gestion qui liste **tous** les objets du genre, désactivés compris, dans l'ordre effectif, avec le nom affiché (nom personnalisé sinon nom d'origine, en texte brut), un interrupteur Activé/Désactivé, des boutons Monter/Descendre (qui échangent avec le voisin du même groupe), un bouton « Ordre d'origine » et un bouton « Enregistrer » qui envoie **une seule écriture** (`PUT …/layout`, `order` complet et `disabled`). « Ordre d'origine » remet l'ordre du catalogue et réactive tous les objets (écriture `{"reset":true}` à l'enregistrement). Un utilisateur non administrateur MUST NOT voir l'écran, son bouton d'accès ni les objets désactivés ; le serveur répond 403 à toute écriture non autorisée. Les erreurs 400, 403 et 503 donnent un message clair et laissent l'écran ouvert avec les modifications.

#### Scenario: Réactivation
- **GIVEN** `wc2` désactivée
- **WHEN** l'administrateur ouvre l'écran de gestion, active `wc2` et enregistre
- **THEN** `wc2` revient dans la liste, à sa position dans l'ordre

#### Scenario: Monter et descendre
- **WHEN** l'administrateur monte `sam` d'un cran et enregistre
- **THEN** `sam` précède `salon` dans le groupe principal, partout

#### Scenario: Ordre d'origine
- **WHEN** l'administrateur appuie sur « Ordre d'origine » puis enregistre
- **THEN** le catalogue complet est affiché dans l'ordre d'origine, nom personnalisé conservé

#### Scenario: Utilisateur non administrateur
- **WHEN** un utilisateur simple ouvre l'écran Éclairage
- **THEN** il ne voit ni le bouton de gestion ni les objets désactivés

#### Scenario: Nom de 40 caractères
- **GIVEN** un nom de 40 caractères
- **THEN** il reste lisible dans l'écran de gestion sur iPhone SE et iPad, sans masquer l'interrupteur ni les boutons

### Requirement: Propagation entre surfaces
Un changement fait sur une surface SHALL arriver sur les autres en moins d'une minute (cloud vers gateway par la synchronisation, gateway vers cloud de même). Deux modifications simultanées donnent partout le même document.

#### Scenario: Cloud vers local
- **WHEN** l'administrateur désactive une lumière sur le portail cloud
- **THEN** elle disparaît du portail local, d'Android et d'iPhone (cloud et réseau local) en moins d'une minute

#### Scenario: Local vers cloud
- **WHEN** `lan_admin` réordonne les volets sur le portail local
- **THEN** le nouvel ordre est visible sur le portail cloud et les apps en moins d'une minute

### Requirement: Aucune écriture dans l'armoire
Les clients MUST NOT écrire la disposition dans l'armoire ni envoyer de commande pour un objet désactivé. Les tests applicatifs s'exécutent sans armoire réelle (`NO_ARMOIRE=1`, mode `no-armoire`).

#### Scenario: Mode no-armoire
- **WHEN** les tests Playwright, instrumentés ou XCUITest s'exécutent
- **THEN** aucune requête vers l'armoire n'est émise
