## Purpose

Garantir que les noms personnalisés de l'app Android se comportent comme sur les autres clients, sans effet sur l'armoire, et qu'une régression est détectée par des tests `NR-*`.

## ADDED Requirements

### Requirement: Tests sans armoire
Les tests des noms SHALL s'exécuter sans armoire réelle : serveurs simulés et horloge contrôlée. Aucun test ne MUST envoyer de commande à une armoire.

#### Scenario: Suite des tests
- **WHEN** les tests des noms s'exécutent avec `NO_ARMOIRE=1`
- **THEN** aucune commande n'atteint une armoire réelle et tous les tests passent

### Requirement: Vecteurs communs
L'app SHALL jouer en Kotlin les vecteurs de test communs `names_vectors.json` (résolution du nom affiché et validation d'un nom), identiques à ceux du cloud, de la gateway, des portails et de l'app iPhone. Elle MUST aboutir aux mêmes résultats qu'eux.

#### Scenario: Résolution
- **WHEN** les vecteurs de résolution s'exécutent
- **THEN** l'app calcule pour chaque cas le même nom affiché que celui du vecteur

#### Scenario: Validation
- **WHEN** les vecteurs de validation s'exécutent
- **THEN** l'app accepte et refuse exactement les mêmes noms que les vecteurs, y compris à 40 et 41 caractères et avec des balises

### Requirement: Parité du catalogue
Le catalogue des objets de l'app SHALL être identique à celui des autres dépôts : mêmes types, clés, pièces, patrons et noms d'origine. Un test MUST échouer dès que le catalogue de l'app diverge, et MUST vérifier que les noms d'origine reproduisent les libellés actuels.

#### Scenario: Divergence
- **WHEN** une clé du catalogue de l'app est modifiée
- **THEN** le test de parité échoue

#### Scenario: Libellés d'origine
- **WHEN** aucun nom n'est personnalisé
- **THEN** chaque objet s'affiche avec le libellé qu'il avait avant cette évolution

### Requirement: Non-régression
Trois tests `NR-*` SHALL protéger les règles essentielles. `NR-android-24` : un utilisateur sans droit d'administrateur n'a pas l'écran « Noms », et le serveur refuse son écriture. `NR-android-25` : les noms d'origine s'affichent quand la route des noms est absente (404). `NR-android-26` : renommer un objet ne change aucun indice ni masque de la table d'indices.

#### Scenario: NR-android-24
- **WHEN** l'app se connecte avec un compte ordinaire puis tente d'écrire un nom
- **THEN** l'écran « Noms » est absent et le serveur simulé refuse l'écriture

#### Scenario: NR-android-25
- **WHEN** le serveur simulé répond 404 aux noms
- **THEN** tous les écrans affichent les noms d'origine et les commandes restent actives

#### Scenario: NR-android-26
- **WHEN** un test renomme chaque objet du catalogue
- **THEN** les indices et masques de la table d'indices sont identiques à ceux d'avant

### Requirement: Tests sur émulateur
Les scénarios principaux (lecture des noms, renommage d'une pièce avec aperçu, nom forcé, rétablissement, utilisateur ordinaire, serveur sans les noms, rafraîchissement) SHALL être couverts sur émulateur, en mode Cloud et en mode Réseau local.

#### Scenario: Les deux modes
- **WHEN** la suite de tests applicatifs s'exécute sur l'émulateur
- **THEN** chaque scénario de noms passe en mode Cloud et en mode Réseau local
