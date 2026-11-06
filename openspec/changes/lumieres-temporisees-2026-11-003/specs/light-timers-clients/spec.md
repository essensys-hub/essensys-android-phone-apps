## Purpose

Offrir le réglage des temporisations sur le portail cloud, le portail local, l'app Android et l'app iPhone, à deux endroits : un menu central et la fiche de chaque lumière.

## ADDED Requirements

### Requirement: Menu central « Temporisations »
Chaque surface SHALL proposer une entrée « Temporisations » qui liste les six lumières (Dressing, WC 1, WC 2, Service, Annexe 1, Annexe 2) avec leur délai courant.

#### Scenario: Liste
- **WHEN** l'administrateur ouvre « Temporisations »
- **THEN** les six lumières s'affichent, avec « inconnue » pour une valeur non lue

### Requirement: Réglage dans la fiche de la lumière
La page des lumières SHALL afficher, pour chacune des six lumières concernées, le même réglage que le menu central. Les autres lumières MUST NOT l'afficher.

#### Scenario: Dressing
- **WHEN** l'administrateur ouvre la fiche du Dressing
- **THEN** le délai du Dressing est visible et modifiable sur place, et le menu central montre la même valeur

### Requirement: Choix et valeur libre
Le réglage MUST offrir 15 min, 30 min, 1 h et Jamais, plus une valeur libre de 1 à 255 minutes. Une valeur hors plage MUST être refusée avant l'envoi. Une valeur courante qui n'est pas un raccourci MUST s'afficher telle quelle.

#### Scenario: Valeur libre
- **WHEN** l'administrateur saisit 7
- **THEN** la requête envoie 7 minutes et l'écran affiche « 7 min »

#### Scenario: Valeur invalide
- **WHEN** l'administrateur saisit 300 ou 0 dans le champ libre
- **THEN** l'envoi est refusé avec un message, aucune requête n'est faite (0 se choisit par « Jamais »)

### Requirement: Administrateur seulement
Le menu et le réglage MUST être masqués aux utilisateurs qui ne sont pas administrateurs de l'armoire. Les apps MUST décoder le rôle à la connexion (cloud et LAN) et le conserver avec la session.

#### Scenario: Utilisateur simple
- **WHEN** un utilisateur de rôle `user` ouvre l'app
- **THEN** ni le menu ni le réglage ne sont visibles

### Requirement: Confirmation de la valeur et hors ligne
Après un envoi, la surface MUST relire la valeur à 5, 10 puis 20 secondes et afficher celle de l'armoire. Hors ligne, les contrôles MUST être désactivés.

#### Scenario: Armoire hors ligne
- **WHEN** l'armoire est hors ligne
- **THEN** les boutons de réglage sont désactivés

### Requirement: Aucune commande réelle dans les tests
Les tests automatiques MUST utiliser le mode no-armoire, un serveur simulé ou le dry-run, et ne jamais atteindre une armoire réelle.

#### Scenario: Garde réseau
- **WHEN** un test déclenche une écriture sans dry-run
- **THEN** la garde no-armoire la bloque
