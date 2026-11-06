## Purpose

Garantir que la session de 30 jours est vérifiée sans armoire réelle, qu'elle ne fuit aucun secret et qu'une régression est détectée par un test NR.

## ADDED Requirements

### Requirement: Tests sans armoire
Les tests de la session mémorisée SHALL s'exécuter sans armoire réelle : serveur simulé et horloge contrôlée. Aucun test ne MUST envoyer de commande à une armoire.

#### Scenario: Passage du temps simulé
- **WHEN** un test avance l'horloge de 30 jours moins une minute, puis d'une minute de plus
- **THEN** la session est valide à la première étape et refusée à la seconde

### Requirement: Non-régression
Un test `NR-android-22` SHALL vérifier qu'une session mémorisée n'est jamais prolongée par l'usage. Un test `NR-android-23` SHALL vérifier que ni le mot de passe, ni le jeton, ni la session mémorisée ne figurent dans le journal ni dans le bundle Support.

#### Scenario: NR-android-22
- **WHEN** l'app renouvelle l'accès chaque jour pendant 29 jours, puis le 31e jour
- **THEN** le 31e jour le renouvellement est refusé et le mot de passe est demandé

#### Scenario: NR-android-23
- **WHEN** l'app se connecte avec la case cochée, puis génère le bundle Support
- **THEN** une recherche du mot de passe, du jeton et de la session mémorisée dans le journal et dans le zip ne trouve rien

### Requirement: Couverture des deux modes
Les scénarios principaux (connexion avec la case cochée, reprise après redémarrage, fin de session) SHALL être couverts en mode Cloud et en mode Réseau local, sur émulateur.

#### Scenario: Les deux modes
- **WHEN** la suite de tests applicatifs s'exécute sur l'émulateur
- **THEN** chaque scénario de reprise et de fin de session passe en mode Cloud et en mode Réseau local
