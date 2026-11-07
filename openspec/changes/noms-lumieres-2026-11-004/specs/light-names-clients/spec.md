## Purpose

Afficher le nom personnalisé de chaque lumière partout où elle apparaît, et permettre à l'administrateur de l'armoire de le modifier, sur le portail cloud, le portail local, l'app Android et l'app iPhone.

## ADDED Requirements

### Requirement: Nom affiché partout
Chaque client SHALL lire les noms (`GET /api/portal/lights/names` en mode cloud, `GET /api/lights/names` en mode local) et afficher `name` quand il est non nul, `default` sinon, partout où la lumière apparaît : listes d'éclairage (principal et indirect), fiche, écran Temporisations, éditeur de scénarios, confirmations et messages de dernière action. Le nom MUST être rendu comme texte brut. Si la lecture échoue, les noms d'origine sont affichés.

#### Scenario: Nom personnalisé
- **GIVEN** `dressing` porte le nom « WC 3 »
- **WHEN** l'utilisateur ouvre la liste d'éclairage et l'écran Temporisations
- **THEN** « WC 3 » apparaît à la place de « Dressing », aux deux endroits

#### Scenario: Lecture impossible
- **WHEN** la lecture des noms échoue
- **THEN** les noms d'origine sont affichés, sans message d'erreur bloquant

#### Scenario: Nom contenant du HTML
- **GIVEN** un nom stocké `a&b` ou contenant des caractères spéciaux
- **THEN** il est affiché tel quel, jamais interprété

### Requirement: Modification par l'administrateur
La fiche de chaque lumière SHALL proposer, aux seuls administrateurs de l'armoire, un champ « Nom » (1 à 40 caractères) avec un bouton d'enregistrement et un bouton « Nom d'origine ». Le client MUST appeler `PUT /api/portal/admin/lights/names/{id}` en mode cloud, `PUT /api/admin/lights/names/{id}` en mode local, valider la longueur avant l'envoi et afficher un message clair en cas d'erreur 400, 403 ou 503.

#### Scenario: Administrateur renomme
- **WHEN** l'administrateur saisit « WC 3 » et enregistre
- **THEN** le nom est mis à jour à l'écran sans rechargement

#### Scenario: Utilisateur non administrateur
- **THEN** le champ de modification n'est pas affiché (le serveur reste la protection : 403)

#### Scenario: Nom d'origine
- **WHEN** l'administrateur appuie sur « Nom d'origine »
- **THEN** `name` est remis à `null` et le nom d'origine réapparaît

### Requirement: Cohérence avec les temporisations
L'écran Temporisations et la fiche de chaque lumière à délai SHALL utiliser le nom affiché, et non plus le libellé d'origine renvoyé par `/lights/timers`.

#### Scenario: Temporisations
- **GIVEN** `wc1` renommée « WC Haut »
- **THEN** l'écran Temporisations liste « WC Haut »

### Requirement: Aucun effet sur l'armoire
Renommer une lumière MUST NOT envoyer d'ordre ni d'indice à l'armoire. Les tests utilisent le mode `no-armoire`.

#### Scenario: Renommage
- **WHEN** un nom est modifié
- **THEN** aucune requête vers `/api/admin/inject` ni `/api/portal/web/actions` n'est émise
