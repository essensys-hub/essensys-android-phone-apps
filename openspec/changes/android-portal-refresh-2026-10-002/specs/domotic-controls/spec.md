## Purpose

Fournit les commandes d'éclairage et de volets de la V1 avec exactement la même sémantique que le portail (mêmes indices et masques de la table d'échange), un retour clair à l'utilisateur et sans risque de rafale de commandes.

## ADDED Requirements

### Requirement: Référentiel d'indices unique
Les indices et masques de commande SHALL être ceux du portail (`essensys-user-portal-frontend`) : éclairage allumer 611–616 / éteindre 605–610, volets ouvrir 617–619 / fermer 620–622, valeur `v` = masque binaire de la pièce ou du volet en chaîne. Ils SHALL être définis dans une table unique de l'app, couverte par des tests de non-régression. Aucun indice de la table d'échange NE SHALL être modifié par ce change.

#### Scenario: Allumer le salon
- **WHEN** l'utilisateur allume le salon
- **THEN** l'app envoie l'injection `k=612, v="128"` (même couple que le portail)

### Requirement: Envoi selon le mode
En cloud, une commande SHALL être envoyée par `POST /api/portal/inject` (`{"k": <int>, "v": "<string>"}`) avec le JWT ; en LAN par `POST /api/admin/inject` avec la session gateway.

#### Scenario: Volet cuisine en LAN
- **WHEN** l'utilisateur ferme le volet cuisine en mode réseau local
- **THEN** l'app appelle `POST https://mon.essensys.local/api/admin/inject` avec l'indice et le masque du portail

### Requirement: Éclairage
L'écran Éclairage SHALL lister les pièces du portail, permettre allumer / éteindre par pièce et « tout allumer / tout éteindre ».

#### Scenario: Tout éteindre
- **WHEN** l'utilisateur choisit « Tout éteindre »
- **THEN** l'app envoie les injections d'extinction de toutes les pièces avec les masques du portail

### Requirement: Volets
L'écran Volets SHALL permettre ouvrir / fermer par volet, par zone et pour tous, et afficher le temps de course configuré lorsqu'il est lisible (`GET /api/portal/exchange?keys=566..589` en cloud).

#### Scenario: Ouvrir tous les volets
- **WHEN** l'utilisateur choisit « Tout ouvrir »
- **THEN** l'app envoie les injections d'ouverture de toutes les zones

### Requirement: Retour et anti-rebond
Chaque commande SHALL afficher un état en cours puis succès (« Commande envoyée — l'armoire exécute sous ~5 s », comme le portail) ou erreur (message serveur JSON ou texte ; 429 « Trop de commandes, patientez » — limite cloud 30/min ; 403 « Accès portail non approuvé »). Les commandes de groupe (« tout allumer ») SHALL être envoyées séquentiellement, une injection par sortie, comme le portail. Un même bouton NE SHALL PAS renvoyer de commande tant que la précédente n'a pas répondu (anti-rebond).

#### Scenario: Double appui
- **WHEN** l'utilisateur appuie deux fois rapidement sur « Allumer » pour la même pièce
- **THEN** une seule injection est envoyée

### Requirement: Mode test (dry-run)
L'app SHALL proposer, dans les réglages, un mode test qui ajoute l'en-tête `X-Essensys-Test-Mode: dry-run` à toutes les commandes ; un bandeau SHALL l'indiquer en permanence. En mode test, une réponse `test_ok` SHALL être affichée comme « Commande validée (test, non exécutée) ».

#### Scenario: Commande en mode test
- **WHEN** le mode test est actif et l'utilisateur allume une pièce
- **THEN** la requête porte l'en-tête dry-run et l'app affiche que la commande n'a pas été exécutée
