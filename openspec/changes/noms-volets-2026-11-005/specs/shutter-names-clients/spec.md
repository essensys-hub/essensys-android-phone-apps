## Purpose

Afficher le nom personnalisé de chaque volet et store partout où il apparaît, et permettre à l'administrateur de l'armoire de le modifier, sur le portail cloud, le portail local, l'app Android et l'app iPhone.

## ADDED Requirements

### Requirement: Nom affiché partout
Chaque client SHALL lire les noms (`GET /api/portal/shutters/names` en mode cloud, `GET /api/shutters/names` en mode local) et afficher `name` quand il est non nul, `default` sinon, partout où le volet apparaît : écran Volets (les deux groupes), fiche, écran « Temps de course », confirmations et messages de dernière action (« Ouvert », « Fermé », « temps de course réglé »). Le nom MUST être rendu comme texte brut. Si la lecture échoue, les noms d'origine sont affichés. Les titres de cartes et les boutons génériques (« Volets », « Tout ouvrir », « Ouvrir », « Fermer ») ne changent pas.

#### Scenario: Nom personnalisé
- **GIVEN** `volet1salon` porte le nom « Volet baie vitrée »
- **WHEN** l'utilisateur ouvre l'écran Volets puis la carte « Temps de course »
- **THEN** « Volet baie vitrée » apparaît à la place de « Volet 1 Salon », aux deux endroits, et dans le message après une commande

#### Scenario: Lecture impossible
- **WHEN** la lecture des noms échoue
- **THEN** les noms d'origine sont affichés, sans message d'erreur bloquant

#### Scenario: Nom contenant du HTML ou des guillemets
- **GIVEN** un nom stocké `a&b` ou `Store "nord"`
- **THEN** il est affiché tel quel, jamais interprété

#### Scenario: Nom de 40 caractères
- **GIVEN** un nom de 40 caractères
- **THEN** il reste lisible dans les listes et dans « Temps de course » sur iPhone SE et iPad (retour à la ligne ou troncature sans masquer les boutons)

### Requirement: Modification par l'administrateur
La fiche de chaque volet SHALL proposer, aux seuls administrateurs de l'armoire, un champ « Nom » (1 à 40 caractères) avec un bouton d'enregistrement et un bouton « Nom d'origine ». Le client MUST appeler `PUT /api/portal/admin/shutters/names/{id}` en mode cloud, `PUT /api/admin/shutters/names/{id}` en mode local, valider la longueur avant l'envoi et afficher un message clair en cas d'erreur 400, 403 ou 503.

#### Scenario: Administrateur renomme
- **WHEN** l'administrateur saisit « Volet baie vitrée » et enregistre
- **THEN** le nom est mis à jour à l'écran sans rechargement

#### Scenario: Utilisateur non administrateur
- **THEN** le champ de modification n'est pas affiché (le serveur reste la protection : 403)

#### Scenario: Nom d'origine
- **WHEN** l'administrateur appuie sur « Nom d'origine »
- **THEN** `name` est remis à `null` et le nom d'origine réapparaît

#### Scenario: Nom invalide
- **WHEN** l'administrateur saisit 41 caractères ou `<b>`
- **THEN** l'envoi est refusé avec un message clair et rien n'est enregistré

### Requirement: Cohérence avec le temps de course
L'écran « Temps de course » (#18) SHALL utiliser le nom affiché, et non plus le libellé d'origine du tableau des volets. Le réglage du temps de course (indices 566 à 585) MUST rester inchangé.

#### Scenario: Temps de course
- **GIVEN** `store` renommé « Store terrasse »
- **THEN** la carte « Temps de course » liste « Store terrasse »

### Requirement: Lumières et noms d'origine préservés
Les noms des lumières (#41) MUST continuer de fonctionner sans changement. Le nom d'origine des volets reste dans la table des clients comme valeur de repli.

#### Scenario: Non-régression lumières
- **WHEN** un volet est renommé
- **THEN** les noms de lumières affichés et leurs tests `NR-*` de #41 sont inchangés

### Requirement: Aucun effet sur l'armoire
Renommer un volet MUST NOT envoyer d'ordre ni d'indice à l'armoire. Les tests utilisent le mode `no-armoire` (`NO_ARMOIRE=1`).

#### Scenario: Renommage
- **WHEN** un nom est modifié
- **THEN** aucune requête vers `/api/admin/inject` ni `/api/portal/web/actions` n'est émise
