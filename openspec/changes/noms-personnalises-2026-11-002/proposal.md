> Ticket : [essensys-hub/essensys-feature-lifecycle#41](https://github.com/essensys-hub/essensys-feature-lifecycle/issues/41). Feature ID : `noms-personnalises-2026-11-002`.
> Feature répartie sur 3 changes du même nom : apps Android et iPhone (un change dans chaque dépôt) et plateforme (cloud, gateway, portails, installations CM5 et Raspberry Pi 4, dans `essensys-memory`). Ce change ne décrit que le code de **essensys-android-phone-apps**.

## Why

Les noms des pièces et des objets (« Petite Chambre 3 », « Volet 1 Salon », « Salle de Bain 1 »…) sont codés en dur dans l'app. Un occupant ne peut pas les adapter à sa maison : « Petite Chambre 3 » ne deviendra jamais « Chambre de Nico ». Les noms choisis doivent aussi être les mêmes sur le portail cloud, le portail local et l'app iPhone.

## What Changes

- L'app **lit les noms personnalisés** de l'installation (cloud en mode Cloud, gateway en mode Réseau local), les garde en cache et les **rafraîchit** au démarrage, au retour au premier plan et toutes les 30 s écran visible.
- Chaque écran affiche le **nom choisi** à la place du libellé codé en dur : éclairage, volets, chauffage, prises de sécurité, chauffe-eau, arrosage, scénarios et éditeur de scénarios, confirmations, état.
- Écran **« Noms »** dans les Réglages, réservé à l'administrateur de l'armoire : modifier un nom (40 caractères au plus, compteur), le rétablir, voir avant d'enregistrer les objets d'une pièce qui prendront le nouveau nom.
- Un nom de pièce se propage par défaut à ses objets ; un nom forcé sur un objet prime.
- Quand le serveur ne fournit pas les noms, l'app affiche les noms d'origine, sans erreur.
- Aucune écriture hors ligne, aucun indice ni commande modifié.
- Version 2.5.0, release `android-v2.5.0`.

## Capabilities

### New Capabilities

- `custom-names-app` : lecture et rafraîchissement des noms, cache, écran « Noms », affichage partout, noms d'origine en secours.
- `custom-names-app-quality` : tests sans armoire, vecteurs communs, parité du catalogue, tests `NR-*`, tests sur émulateur.

### Modified Capabilities

Aucune (les specs existantes de ce dépôt ne sont pas archivées sous `openspec/specs/`).

## Impact

- **Code** : `data/control` (table d'indices : clés et libellés d'origine), nouveau paquet de noms (référentiel, cache, règle de nom affiché, validation), `ui/screens` (éclairage, volets, chauffage, scénarios et éditeur, prises de sécurité, chauffe-eau, arrosage, Réglages), `data/http` (appels `/api/names`).
- **Serveur** : `GET`, `PUT` et `DELETE /api/names` du cloud et de la gateway, décrits dans le change du même nom de `essensys-memory`. **L'app ne sort qu'après les backends et les portails.**
- **Compatibilité** : un serveur qui ne connaît pas les noms répond 404 ; l'app garde alors les noms d'origine.
- **Données personnelles** : les noms ne figurent ni dans le journal de l'app ni dans le bundle Support.
- **Dépendances** : aucune nouvelle.
