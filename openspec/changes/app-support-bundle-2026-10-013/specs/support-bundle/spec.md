## Purpose

Permet à un utilisateur de l'appli Android d'envoyer au support, en un appui, un diagnostic technique de sa situation réelle (appareil, réseau, portail, gateway, réglages) et le journal de l'appli, sans exposer de secret.

## ADDED Requirements

### Requirement: Accès au support
L'appli SHALL proposer un bouton « Contacter le support » dans Réglages et sur l'écran de connexion. Le bouton SHALL fonctionner avec ou sans session ouverte, en mode cloud comme en mode local.

#### Scenario: Utilisateur connecté
- **WHEN** l'utilisateur ouvre Réglages et appuie sur « Contacter le support »
- **THEN** l'écran Support s'ouvre et lance les tests de diagnostic

#### Scenario: Utilisateur non connecté
- **WHEN** l'utilisateur appuie sur « Contacter le support » depuis l'écran de connexion
- **THEN** les tests qui ne demandent pas de session sont lancés, et les tests du portail indiquent « non connecté » au lieu d'échouer

### Requirement: Tests de diagnostic en lecture seule
L'appli SHALL lancer les tests suivants et SHALL n'envoyer aucune commande à l'armoire (aucune injection, aucune écriture dans la table d'échange) :
- appareil et appli : version et build de l'appli, version d'Android, modèle, langue, fuseau horaire, écart d'horloge avec le serveur (en-tête `Date`) ;
- réseau : type de connexion (Wi-Fi, données mobiles, VPN, aucune), résolution DNS de mon.essensys.fr, temps de réponse HTTPS sur 5 essais (min, moyenne, max, échecs), validité du certificat TLS ;
- portail cloud : réponse de `GET /api/portal/health`, session valide, expirée ou absente, armoire associée, date du dernier contact, état « en ligne » calculé par l'appli et règle appliquée, dernière action et son résultat ;
- mode local : gateway joignable ou non (`GET /health`) et temps de réponse ;
- réglages : mode de connexion, mode test actif ou non.

Chaque test SHALL avoir un résultat parmi « OK », « Attention », « Échec » ou « Non applicable », avec un détail lisible. Un test qui échoue ou dépasse son délai NE SHALL PAS bloquer les autres. L'ensemble SHALL se terminer en moins de 30 secondes.

#### Scenario: Téléphone en 4G avec mode test actif
- **WHEN** les tests tournent sur un téléphone en données mobiles avec le mode test actif
- **THEN** le résultat indique « Données mobiles » et « Mode test actif » en « Attention »

#### Scenario: Portail injoignable
- **WHEN** mon.essensys.fr ne répond pas
- **THEN** les tests réseau et portail passent en « Échec » avec la cause (DNS, délai dépassé, certificat), et les tests appareil et réglages restent renseignés

#### Scenario: Aucune commande envoyée
- **WHEN** les tests de diagnostic tournent
- **THEN** aucune requête vers `/inject`, `/inject/batch` ou `/web/actions` n'est émise

### Requirement: Dernier contact de l'armoire
Le test « Armoire associée » SHALL afficher la date du dernier contact de l'armoire en heure locale (`dd/MM/yyyy HH:mm`), suivie de la durée écoulée (« il y a 3 min », « il y a 5 h », « il y a 281 j »). Son statut SHALL suivre cette règle, dans l'ordre (essensys-hub/essensys-android-phone-apps#33) :
- date absente ou illisible : « Attention », détail « dernier contact inconnu » ;
- armoire vue par le serveur depuis 2 min au plus : « OK » ;
- sinon, gateway en ligne : « OK », avec la mention « contact assuré par la gateway » ;
- sinon, dernier contact de plus de 24 h : « Attention » ;
- sinon : « OK ».

#### Scenario: Armoire ancienne derrière une gateway
- **WHEN** la gateway est en ligne et le dernier contact direct de l'armoire date de 9 mois
- **THEN** le test est « OK » et le détail montre la date locale, « il y a 281 j » et « contact assuré par la gateway »

#### Scenario: Armoire ancienne sans gateway
- **WHEN** aucune gateway n'est en ligne et le dernier contact de l'armoire date de plus de 24 h
- **THEN** le test est « Attention » et le détail montre la date locale et la durée écoulée

#### Scenario: Date inconnue
- **WHEN** le serveur ne donne pas de date de dernier contact
- **THEN** le test est « Attention » avec « dernier contact inconnu »

### Requirement: Journal interne
L'appli SHALL tenir un journal des 500 derniers événements, conservé entre deux lancements : requêtes HTTP (méthode, chemin sans paramètres, code de réponse, durée, erreur réseau) et événements de l'appli (connexion, déconnexion, changement d'état en ligne, changement de mode, erreurs). Le journal NE SHALL contenir ni corps de requête ou de réponse, ni en-tête d'authentification, ni cookie.

#### Scenario: Journal d'une commande
- **WHEN** l'utilisateur allume une lumière
- **THEN** le journal contient `POST /api/portal/inject/batch`, le code de réponse et la durée, sans les valeurs envoyées

### Requirement: Contenu et confidentialité du bundle
Le bundle SHALL être un fichier zip nommé `essensys-support-android-<version>-<AAAAMMJJ-HHMMSS>.zip` contenant `resume.txt` (résumé lisible en français), `diagnostics.json` (résultats détaillés) et `journal.jsonl` (journal interne). Le bundle NE SHALL contenir ni mot de passe, ni token, ni cookie, ni la clé d'accès à 32 chiffres, ni code d'alarme, ni adresse IP publique.

#### Scenario: Bundle sans secret
- **WHEN** un bundle est produit avec une session cloud ouverte et un cookie LAN enregistré
- **THEN** aucune des valeurs du token, du cookie ou du mot de passe n'apparaît dans les fichiers du zip

### Requirement: Résumé et envoi par email
L'appli SHALL montrer le résumé des tests avant l'envoi. Sur « Envoyer au support », elle SHALL ouvrir une appli email avec : destinataire l'adresse support configurée (`support_essensys@rhinosys.fr`), objet « Support Essensys — <version> — <date> », le résumé dans le corps et le zip en pièce jointe. L'utilisateur reste libre de modifier ou d'annuler le message. Si aucune appli email n'est installée, l'appli SHALL proposer le partage du zip par une autre appli.

#### Scenario: Envoi
- **WHEN** l'utilisateur appuie sur « Envoyer au support » après les tests
- **THEN** l'appli email s'ouvre avec le destinataire, l'objet, le résumé et le zip joint, sans rien envoyer d'elle-même

#### Scenario: Pas d'appli email
- **WHEN** aucune appli ne sait traiter un email
- **THEN** la feuille de partage Android s'ouvre avec le zip
