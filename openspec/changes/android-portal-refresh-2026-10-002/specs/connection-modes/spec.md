## Purpose

Permet à l'utilisateur de piloter son installation en cloud (mon.essensys.fr) ou directement en LAN (gateway mon.essensys.local), en sachant toujours à quoi l'app est connectée et dans quel état est la gateway.

## ADDED Requirements

### Requirement: Choix explicite du mode
L'app SHALL proposer deux modes, « Cloud » (`https://mon.essensys.fr`) et « Réseau local » (`https://mon.essensys.local`, hôte modifiable), choisis par l'utilisateur à la connexion et modifiables dans les réglages. Le mode actif SHALL être visible en permanence (en-tête).

#### Scenario: Changement de mode
- **WHEN** l'utilisateur passe de Cloud à Réseau local dans les réglages
- **THEN** la session cloud est fermée, l'écran de connexion LAN s'affiche et l'en-tête indique « Réseau local » après connexion

### Requirement: HTTPS uniquement
Toutes les communications SHALL utiliser HTTPS. Le trafic en clair SHALL être interdit au niveau de l'app. En LAN, l'app SHALL accepter le certificat de la gateway uniquement via la PKI Essensys déclarée (network-security-config), sans désactiver la vérification TLS.

#### Scenario: URL en HTTP
- **WHEN** l'utilisateur saisit un hôte LAN en `http://`
- **THEN** l'app le refuse et explique que seule une connexion sécurisée est possible

### Requirement: Pas de mode démo implicite
L'app NE SHALL JAMAIS basculer automatiquement vers des données fictives en cas d'erreur réseau. Toute erreur de connexion SHALL être affichée comme telle, avec une action « Réessayer ».

#### Scenario: Serveur injoignable
- **WHEN** le serveur est injoignable
- **THEN** l'app affiche « Connexion impossible à <hôte> » et un bouton « Réessayer », sans afficher de pièces ou d'états fictifs

### Requirement: Liaison de l'armoire (cloud)
En mode cloud, l'app SHALL vérifier `GET /api/portal/link-request/status`. Si aucune armoire n'est liée au compte, elle SHALL afficher l'état de la demande et permettre d'en soumettre une (`POST /api/portal/link-request` avec numéro de série et message), et NE SHALL PAS afficher les commandes.

#### Scenario: Compte sans armoire liée
- **WHEN** un utilisateur cloud sans armoire liée se connecte
- **THEN** l'app affiche l'écran de liaison au lieu des commandes

### Requirement: État de la gateway et dernière action
En cloud, l'app SHALL afficher si la gateway est en ligne (`GET /api/portal/session`, champ `gateway.online`, rafraîchi toutes les 60 s au premier plan) et la dernière action (`GET /api/portal/history/latest`, rafraîchi toutes les 2 s au premier plan seulement, « en cours » tant que `isDone` est faux). Le cloud mettant les commandes en file même hors ligne, l'app SHALL désactiver les commandes et l'expliquer quand la gateway est hors ligne. En LAN, la gateway n'exposant ni état ni historique, l'app SHALL vérifier la session (`GET /api/user/me`) à l'ouverture et afficher « Réseau local — connexion directe à l'armoire » sans dernière action.

#### Scenario: Gateway hors ligne
- **WHEN** la session indique que la gateway est hors ligne
- **THEN** l'en-tête affiche « Armoire hors ligne » et les boutons de commande sont désactivés

#### Scenario: Mode réseau local
- **WHEN** l'utilisateur est connecté en mode réseau local
- **THEN** l'en-tête affiche « Réseau local » et aucun appel à `/api/portal/*` n'est fait

#### Scenario: App en arrière-plan
- **WHEN** l'app passe en arrière-plan
- **THEN** aucun rafraîchissement périodique n'est effectué
