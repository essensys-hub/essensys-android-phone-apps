## Purpose

Faire apparaître dans l'app Android les noms choisis par l'administrateur de l'armoire pour les pièces et les objets, et lui permettre de les modifier, en mode Cloud comme en mode Réseau local. Les règles de nom affiché, de validation et de droits sont celles du change du même nom de `essensys-memory`.

## ADDED Requirements

### Requirement: Lecture des noms
L'app SHALL lire les noms personnalisés de l'installation auprès du serveur du mode courant : le cloud en mode Cloud, la gateway en mode Réseau local. Elle MUST les lire après la connexion, puis les garder à jour sans action de l'utilisateur. Un utilisateur ne MUST voir que les noms de son installation.

#### Scenario: Connexion en mode Cloud
- **WHEN** l'utilisateur se connecte en mode Cloud
- **THEN** l'app charge les noms de son installation depuis le cloud avant d'afficher l'accueil complet

#### Scenario: Mode Réseau local
- **WHEN** l'utilisateur est connecté en mode Réseau local
- **THEN** l'app charge les noms depuis la gateway, même sans accès à internet

### Requirement: Rafraîchissement
L'app SHALL rafraîchir les noms au démarrage, au retour au premier plan et toutes les 30 secondes tant qu'un écran est visible. Elle MUST ne recharger les noms que s'ils ont changé : une réponse « inchangé » ne doit ni modifier l'écran ni consommer de données notables. Un nom modifié sur un autre environnement MUST apparaître en moins d'une minute.

#### Scenario: Changement venu du portail
- **GIVEN** l'app ouverte sur l'écran éclairage
- **WHEN** l'administrateur renomme une lumière depuis le portail
- **THEN** le nouveau nom s'affiche en moins d'une minute, sans action de l'utilisateur

#### Scenario: Retour au premier plan
- **WHEN** l'utilisateur rouvre l'app après quelques minutes
- **THEN** les noms sont rafraîchis immédiatement

#### Scenario: Rien de nouveau
- **WHEN** le serveur répond que les noms sont inchangés
- **THEN** l'écran reste identique

### Requirement: Cache des derniers noms
L'app SHALL conserver les derniers noms reçus sur le téléphone et les afficher dès le démarrage, y compris sans réseau. Le cache MUST être propre à l'installation et MUST être effacé quand l'utilisateur se déconnecte ou change d'installation.

#### Scenario: Démarrage sans réseau
- **GIVEN** des noms déjà reçus, puis le téléphone sans réseau
- **WHEN** l'utilisateur ouvre l'app
- **THEN** les derniers noms connus s'affichent

#### Scenario: Déconnexion
- **WHEN** l'utilisateur se déconnecte
- **THEN** les noms conservés sont effacés du téléphone

### Requirement: Noms affichés partout
L'app SHALL afficher le nom choisi partout où un objet ou une pièce apparaît : éclairage, volets, chauffage, prises de sécurité, chauffe-eau, arrosage, scénarios, éditeur de scénarios, messages de confirmation, état et dernière action. Un libellé d'origine MUST NOT rester affiché à la place d'un nom choisi.

#### Scenario: Écran éclairage
- **GIVEN** la pièce « Petite Chambre 3 » renommée « Chambre de Nico »
- **WHEN** l'utilisateur ouvre l'écran éclairage
- **THEN** la lumière de la pièce et son chevet s'affichent sous « Chambre de Nico » et « Chambre de Nico (chevet) »

#### Scenario: Volets
- **WHEN** l'utilisateur ouvre l'écran des volets
- **THEN** le volet de cette pièce s'affiche « Volet Chambre de Nico »

#### Scenario: Éditeur de scénarios
- **WHEN** l'utilisateur choisit les lumières et volets d'un scénario
- **THEN** la liste montre les noms choisis

#### Scenario: Confirmation
- **WHEN** l'utilisateur demande à couper les prises de sécurité renommées « Prises du garage »
- **THEN** la confirmation emploie « Prises du garage »

### Requirement: Écran « Noms »
Les Réglages SHALL proposer un écran « Noms » aux seuls utilisateurs que le serveur désigne comme administrateurs (`can_rename`). L'écran SHALL lister les pièces et les objets par catégorie, avec pour chaque ligne le nom affiché et son origine : personnalisé, hérité de la pièce ou d'origine. L'administrateur SHALL pouvoir modifier un nom et le rétablir. Les autres utilisateurs MUST NOT voir cet écran ni aucun contrôle de renommage.

#### Scenario: Administrateur
- **WHEN** l'administrateur ouvre les Réglages
- **THEN** il voit l'écran « Noms » avec toutes les catégories

#### Scenario: Utilisateur ordinaire
- **WHEN** un utilisateur sans droit d'administrateur ouvre les Réglages
- **THEN** l'écran « Noms » n'apparaît pas

#### Scenario: Renommer
- **WHEN** l'administrateur saisit « Chambre de Nico » pour la pièce « Petite Chambre 3 » et enregistre
- **THEN** l'app envoie le nom au serveur, puis affiche le nom enregistré et l'origine « personnalisé »

#### Scenario: Rétablir
- **WHEN** l'administrateur rétablit le nom d'un volet forcé
- **THEN** le volet retrouve son nom hérité de la pièce, ou son nom d'origine

### Requirement: Aperçu avant de renommer une pièce
Avant d'enregistrer le nom d'une pièce, l'app SHALL montrer à l'administrateur les objets dont le nom affiché va changer et ceux au nom forcé qui ne changeront pas.

#### Scenario: Aperçu
- **WHEN** l'administrateur saisit un nouveau nom pour « Petite Chambre 3 »
- **THEN** l'app liste la lumière de chevet et le volet qui prendront ce nom, et signale un objet au nom forcé qui ne changera pas

### Requirement: Saisie contrôlée
L'app SHALL limiter la saisie à 40 caractères, afficher le nombre de caractères restants et refuser un nom invalide avant tout envoi. Un nom refusé par le serveur MUST rester dans le champ avec le message du serveur, sans perdre ce que l'administrateur a tapé.

#### Scenario: Trop long
- **WHEN** l'administrateur dépasse 40 caractères
- **THEN** l'app empêche l'enregistrement et indique la limite

#### Scenario: Refus du serveur
- **WHEN** le serveur refuse un nom
- **THEN** l'app affiche son message et garde le texte saisi

#### Scenario: Balises
- **WHEN** un nom contient « <b>Nico</b> »
- **THEN** il s'affiche tel quel, sans mise en forme

### Requirement: Pas d'écriture hors ligne
L'app MUST NOT enregistrer un nom quand le serveur est injoignable : elle SHALL refuser l'enregistrement avec un message clair et conserver le texte saisi.

#### Scenario: Pas de réseau
- **WHEN** l'administrateur enregistre un nom sans réseau
- **THEN** l'app affiche que le nom n'a pas pu être enregistré et garde le texte dans le champ

### Requirement: Noms d'origine en secours
Quand le serveur ne fournit pas les noms (route absente, erreur, serveur plus ancien), l'app SHALL afficher les noms d'origine, ou les derniers noms connus, sans erreur visible et sans bloquer aucune commande.

#### Scenario: Serveur sans les noms
- **WHEN** le serveur répond 404 à la demande de noms
- **THEN** l'app affiche les noms d'origine et toutes les commandes restent utilisables

#### Scenario: Erreur passagère
- **WHEN** le serveur répond une erreur 5xx au rafraîchissement
- **THEN** l'app garde les noms déjà affichés et réessaie au cycle suivant

### Requirement: Noms et confidentialité
Les noms personnalisés MUST NOT figurer dans le journal de l'app ni dans le bundle Support. Ils ne MUST PAS être envoyés ailleurs qu'au serveur de l'installation.

#### Scenario: Bundle Support
- **WHEN** l'utilisateur génère le bundle Support avec des noms personnalisés
- **THEN** le zip et le journal ne contiennent aucun de ces noms

### Requirement: Aucune commande modifiée
La gestion des noms MUST NOT modifier un indice, un masque ni une commande envoyée à l'armoire.

#### Scenario: Commande d'un volet renommé
- **GIVEN** un volet renommé
- **WHEN** l'utilisateur l'ouvre
- **THEN** la commande envoyée porte les mêmes indice et masque qu'avant le renommage
