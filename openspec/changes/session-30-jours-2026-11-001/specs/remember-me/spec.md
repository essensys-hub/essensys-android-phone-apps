## Purpose

Permettre à l'utilisateur de ne pas ressaisir son mot de passe pendant 30 jours sur l'app, en mode Cloud comme en mode Réseau local, sans jamais conserver le mot de passe sur le téléphone.

## ADDED Requirements

### Requirement: Option « Se souvenir de moi »
L'écran de connexion SHALL proposer une case « Se souvenir de moi (30 jours) », en mode Cloud et en mode Réseau local. La case MUST être décochée par défaut et ne MUST PAS mémoriser le choix de la connexion précédente.

#### Scenario: Case décochée par défaut
- **WHEN** l'utilisateur ouvre l'écran de connexion, y compris après une déconnexion où il l'avait cochée
- **THEN** la case « Se souvenir de moi (30 jours) » est décochée

#### Scenario: Case présente dans les deux modes
- **WHEN** l'utilisateur bascule entre Cloud et Réseau local
- **THEN** la case reste visible et utilisable dans chacun des deux modes

### Requirement: Mot de passe non demandé pendant 30 jours
Quand l'utilisateur s'est connecté avec la case cochée, l'app SHALL rester connectée pendant 30 jours fixes à compter de la saisie du mot de passe. Elle MUST rester connectée après la fermeture de l'app, après son arrêt forcé et après le redémarrage du téléphone. Utiliser l'app ne MUST PAS prolonger ces 30 jours.

#### Scenario: Reprise après redémarrage du téléphone
- **GIVEN** une connexion avec la case cochée il y a 10 jours
- **WHEN** le téléphone redémarre et l'utilisateur ouvre l'app
- **THEN** l'app s'ouvre sur l'accueil, sans demander le mot de passe

#### Scenario: Durée fixe
- **GIVEN** une connexion avec la case cochée, puis une utilisation quotidienne
- **WHEN** 30 jours se sont écoulés depuis la saisie du mot de passe
- **THEN** l'app demande le mot de passe, même si elle a été utilisée la veille

### Requirement: Renouvellement transparent de l'accès cloud
En mode Cloud, l'app SHALL obtenir un nouvel accès sans intervention de l'utilisateur quand l'accès courant a expiré, tant que la session mémorisée est valide. Si plusieurs requêtes échouent en même temps pour cette raison, l'app MUST ne demander qu'un seul renouvellement et rejouer ensuite chacune d'elles une fois.

#### Scenario: Accès expiré, session mémorisée valide
- **GIVEN** une session mémorisée de 3 jours et un accès cloud de 24 h expiré
- **WHEN** l'utilisateur ouvre un écran qui interroge le portail
- **THEN** l'écran s'affiche avec ses données, sans écran de connexion ni message d'erreur

#### Scenario: Plusieurs requêtes simultanées
- **WHEN** quatre requêtes partent en même temps avec un accès expiré
- **THEN** un seul renouvellement est demandé au serveur et les quatre requêtes aboutissent

#### Scenario: Renouvellement impossible
- **WHEN** le serveur refuse le renouvellement parce que la session est expirée, révoquée ou le compte désactivé
- **THEN** l'app efface la session et affiche l'écran de connexion avec un message qui dit que la session est terminée

### Requirement: Session non mémorisée inchangée
Quand la case est décochée, l'app SHALL se comporter comme avant cette évolution : aucune session mémorisée n'est demandée ni conservée.

#### Scenario: Case décochée
- **WHEN** l'utilisateur se connecte sans cocher la case, puis son accès de 24 h expire
- **THEN** l'app demande le mot de passe

#### Scenario: Serveur sans prise en charge
- **WHEN** l'utilisateur coche la case mais le serveur ne renvoie aucune session mémorisée
- **THEN** l'app se connecte normalement, sans erreur, et se comporte comme case décochée

### Requirement: Fin de la session mémorisée
La session mémorisée MUST se terminer, côté téléphone et côté serveur, quand l'utilisateur se déconnecte. Elle MUST aussi se terminer quand le mot de passe est changé ou réinitialisé, depuis n'importe quel appareil, et quand le compte est désactivé.

#### Scenario: Déconnexion
- **WHEN** l'utilisateur appuie sur « Se déconnecter »
- **THEN** la session mémorisée est supprimée du téléphone et le serveur la refuse ensuite

#### Scenario: Mot de passe changé sur un autre appareil
- **GIVEN** une session mémorisée sur ce téléphone
- **WHEN** le mot de passe est changé depuis le portail
- **THEN** le prochain renouvellement échoue et l'app redemande le mot de passe

#### Scenario: Déconnexion hors ligne
- **WHEN** l'utilisateur se déconnecte sans réseau
- **THEN** la session est supprimée du téléphone immédiatement, et l'app n'affiche aucune erreur

### Requirement: Stockage sécurisé
L'app MUST NOT conserver le mot de passe sur le téléphone. La session mémorisée SHALL être stockée chiffrée et ne MUST PAS être incluse dans les sauvegardes ni les transferts vers un autre téléphone. Elle ne MUST PAS apparaître dans le journal de l'app ni dans le bundle Support.

#### Scenario: Mot de passe absent
- **WHEN** l'utilisateur s'est connecté avec la case cochée
- **THEN** aucune donnée du téléphone ne contient son mot de passe

#### Scenario: Bundle Support
- **WHEN** l'utilisateur génère le bundle Support avec une session mémorisée active
- **THEN** le zip ne contient ni la session mémorisée, ni le jeton, ni le cookie

### Requirement: Date de fin visible
Les Réglages SHALL indiquer, tant qu'une session mémorisée est active, la date jusqu'à laquelle le mot de passe ne sera pas demandé, en heure locale.

#### Scenario: Session active
- **GIVEN** une connexion avec la case cochée le 2026-11-10
- **WHEN** l'utilisateur ouvre les Réglages
- **THEN** il voit « Connecté jusqu'au 10/12/2026 »

#### Scenario: Session non mémorisée
- **WHEN** l'utilisateur est connecté sans avoir coché la case
- **THEN** les Réglages n'affichent aucune date de fin
