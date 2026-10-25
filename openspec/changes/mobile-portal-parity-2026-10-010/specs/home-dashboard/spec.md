## Purpose

Fait de l'accueil le point d'entrée vers toutes les fonctions de l'app, avec les scénarios rapides, et définit la navigation commune à Android et iPhone.

## ADDED Requirements

### Requirement: Navigation
L'app SHALL avoir 5 onglets, dans cet ordre et sur les deux plateformes : Accueil, Éclairage, Volets, Chauffage, Réglages. Scénarios, Chauffe-eau, Arrosage et Alarme SHALL être accessibles depuis l'accueil, avec un retour vers l'accueil.

#### Scenario: Accès au chauffe-eau
- **WHEN** l'utilisateur touche la tuile « Chauffe-eau » de l'accueil
- **THEN** l'écran Chauffe-eau s'ouvre et le bouton retour ramène à l'accueil

### Requirement: Accueil
L'accueil SHALL afficher l'état de l'armoire et la dernière action (comme en v2.0.0), une rangée de scénarios rapides (Je sors, Je rentre, Je vais me coucher, Je me lève) qui se lancent en un appui, et une tuile par fonction : Éclairage, Volets, Chauffage, Scénarios, Chauffe-eau, Arrosage, Alarme. La carte « Bientôt disponible » SHALL être supprimée.

#### Scenario: Lancement rapide
- **WHEN** l'utilisateur touche « Je sors » sur l'accueil
- **THEN** le scénario 2 est lancé, avec le même retour que depuis l'écran Scénarios

### Requirement: Armoire hors ligne
Quand l'armoire est hors ligne, les scénarios rapides et toutes les commandes des nouveaux écrans SHALL être désactivés, comme l'éclairage et les volets en v2.0.0. La consultation (planning, définitions) SHALL rester possible.

#### Scenario: Hors ligne
- **WHEN** l'état de l'armoire passe hors ligne
- **THEN** « Je sors » et les boutons de commande sont désactivés, et le planning de chauffage reste lisible

### Requirement: Compte et armoire
En cloud, les Réglages SHALL afficher le nom et l'email du compte, le numéro de série de l'armoire et la date de dernier contact, lus dans `GET /api/portal/session`.

#### Scenario: Réglages cloud
- **WHEN** l'utilisateur connecté en cloud ouvre les Réglages
- **THEN** il voit son email et le numéro de série de son armoire
