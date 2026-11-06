## Context

Voir `proposal.md` (Why). État actuel :

- Les libellés des pièces et des objets sont des chaînes de `IndexTable` (lumières, volets, zones de chauffage, modes), lues directement par `LightingScreen`, `ShuttersScreen`, `HeatingScreen`, `ScenariosScreen` et ses éditeurs, `ComfortScreens` (chauffe-eau, arrosage, prises de sécurité) et `MainScreens`. Les **clés** (`ptchamb3`, `volet1ptchamb`, `sdb1`…) sont déjà stables.
- L'app parle au cloud ou à la gateway selon `ConnectionMode`, par `ApiCaller` (qui sait déjà renouveler un accès expiré) ; la session et les préférences vivent dans `PersistentSessionStore` (`DataStore`).
- Les règles de nom affiché, de validation, de droits et l'API (`GET`, `PUT`, `DELETE /api/names`, `ETag`, 304, `since`, `can_rename`, codes `name_invalid`, `names_forbidden`, `name_unknown_object`) sont décidées dans le change du même nom de `essensys-memory` (décisions 1 à 3, 5, 6, 9 et 10) : ce document ne les redéfinit pas.

## Goals / Non-Goals

**Goals:**
- Le même nom affiché que sur les portails et l'app iPhone, prouvé par des vecteurs de test communs.
- Aucun écran qui affiche un libellé d'origine à la place d'un nom choisi.
- Une app qui reste entièrement utilisable quand le serveur ne connaît pas les noms.

**Non-Goals:**
- Écriture hors ligne, historique des noms, raccourci de renommage depuis l'écran d'un objet (appui long).
- Noms dans les notifications ou l'assistant MCP.
- Choix de la langue des noms.

## Decisions

**1. Un référentiel de noms, séparé de la table d'indices.** `NamesRepository` détient `{ révision, entrées, can_rename }`, appelle l'API du mode courant (cloud ou gateway) et expose un flux d'état que les écrans observent. `IndexTable` garde les clés, les indices et les noms d'origine : **elle ne change pas de comportement**. *Alternative écartée :* modifier les libellés dans `IndexTable` (la table est verrouillée par les tests `NR-*` des indices et ne doit pas dépendre du réseau).

**2. `displayName(kind, clé)` partout.** Une seule fonction applique la règle : nom forcé, sinon patron rempli avec le nom de la pièce, sinon nom d'origine. Elle reprend le catalogue copié (`catalog.json`, mêmes types, clés, pièces et patrons que les autres dépôts) et joue les vecteurs communs `names_vectors.json`. Chaque écran qui lisait un libellé de `IndexTable` appelle `displayName` ; la table reste la source des clés.

**3. Cache persistant dans le `DataStore` existant.** Les dernières entrées et leur révision sont conservées en clair (un nom n'est pas un secret) et **effacées à la déconnexion** et au changement d'installation. Elles ne passent ni par le journal ni par le bundle Support (même filtre que les secrets).

**4. Rafraîchissement.** Au démarrage et après la connexion, au retour au premier plan, puis toutes les 30 s tant qu'un écran est visible, avec `If-None-Match` : une réponse 304 ne change rien. Un `Mutex` évite deux rafraîchissements simultanés. Après une écriture, l'app reprend l'entrée renvoyée sans attendre le cycle. Un 404 fait retomber sur les noms d'origine et cesse d'interroger pendant la session ; une erreur 5xx ou un réseau absent garde les noms affichés.

**5. Écriture.** `PUT` et `DELETE` ne partent que si le serveur a répondu `can_rename`. Une erreur du serveur (`name_invalid`, `names_forbidden`, `name_unknown_object`) est affichée telle quelle sous le champ, qui garde la saisie. Hors ligne, l'écriture est refusée avec un message : aucune file d'attente dans l'app (la gateway porte la reprise).

**6. Écran « Noms ».** Une page des Réglages visible seulement si `can_rename` : catégories repliables, une ligne par pièce ou objet (nom affiché et puce « personnalisé », « hérité de la pièce » ou « d'origine »), feuille de modification avec compteur sur 40 et boutons « Enregistrer » et « Rétablir ». Pour une pièce, l'aperçu liste les objets qui changent et ceux au nom forcé qui ne changent pas, calculés par `displayName` avant l'envoi.

**7. Validation côté app.** Les mêmes règles que le serveur (1 à 40 points de code, `trim`, espaces réduits, NFC, caractères de contrôle et de direction refusés), pour éviter un aller-retour inutile ; le serveur reste l'arbitre.

## Risks / Trade-offs

- **`displayName` oublié sur un écran** : un libellé d'origine resterait visible → test instrumenté qui renomme une pièce et contrôle chaque écran, et revue des usages de `IndexTable`.
- **Divergence de la règle de nom entre clients** → vecteurs communs et parité du catalogue dans chaque dépôt.
- **Droits du propriétaire** : s'il n'a pas le rôle d'administrateur, l'écran reste masqué → voir le change de `essensys-memory` (vérification en production).
- **Interrogation toutes les 30 s** : coût réseau et batterie limités par l'`ETag` et l'arrêt quand l'app n'est pas visible.
- **Nom personnel** (« Chambre de Nico ») : cache en clair sur le téléphone, effacé à la déconnexion, absent du journal et du bundle Support.

## Migration Plan

1. Backends puis portails déployés d'abord ; sans effet visible tant que l'app 2.5.0 n'est pas installée.
2. App 2.5.0 : sans nom personnalisé, rien ne change ; sur un serveur ancien, elle reste sur les noms d'origine.
3. Retour arrière : désactiver la route côté serveur ; l'app retombe sur les noms d'origine sans migration.
