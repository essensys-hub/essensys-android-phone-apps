## Purpose

Permet de mettre en service et d'arrêter l'alarme depuis l'app avec le code à 4 chiffres, sans que l'app ne conserve ni ne divulgue ce code.

## ADDED Requirements

### Requirement: Commande d'alarme
En cloud, l'écran Alarme SHALL permettre « Mettre en service » et « Arrêter » après saisie d'un code de 4 chiffres, et SHALL appeler `POST /api/portal/web/actions` avec `{alarme:"on"|"off", codealarme:"<4 chiffres>"}`. Les boutons SHALL rester désactivés tant que le code n'a pas 4 chiffres. Une confirmation SHALL précéder l'arrêt.

#### Scenario: Mise en service
- **WHEN** l'utilisateur saisit 4 chiffres et choisit « Mettre en service »
- **THEN** l'app appelle `/api/portal/web/actions` avec `alarme:"on"` et affiche « Commande envoyée »

### Requirement: Mode réseau local
En LAN, l'écran Alarme SHALL afficher « Disponible en mode cloud seulement » et NE SHALL envoyer aucune commande, tant que la gateway n'expose pas de route d'alarme compatible avec la session LAN.

#### Scenario: Alarme en LAN
- **WHEN** l'utilisateur ouvre l'écran Alarme en mode réseau local
- **THEN** aucun champ de code n'est proposé et le message explique d'utiliser le mode cloud

### Requirement: Protection du code
Le code d'alarme NE SHALL être ni stocké (préférences, Keystore ou Keychain, état restauré), ni journalisé, ni inclus dans un rapport de plantage ou une capture de test. Le champ SHALL être masqué, numérique, limité à 4 chiffres, et SHALL être vidé après chaque envoi, réussi ou non, et quand l'app passe en arrière-plan. En mode test, l'app NE SHALL PAS afficher les paramètres validés renvoyés par le serveur pour cette commande.

#### Scenario: Retour en arrière-plan
- **WHEN** l'utilisateur saisit 2 chiffres puis passe l'app en arrière-plan
- **THEN** au retour, le champ est vide

#### Scenario: Journaux
- **WHEN** une commande d'alarme est envoyée avec la journalisation HTTP de debug active
- **THEN** le corps de la requête n'apparaît pas dans les journaux

### Requirement: Pas d'état affiché
La commande étant en boucle ouverte, l'app SHALL indiquer que l'envoi ne garantit pas l'état de l'alarme (« Vérifiez l'état sur le clavier de l'armoire ») et NE SHALL PAS afficher d'état « en service » ou « arrêtée ».

#### Scenario: Après envoi
- **WHEN** la commande « Mettre en service » réussit
- **THEN** l'app affiche « Commande envoyée » et le rappel de vérification, sans état d'alarme
