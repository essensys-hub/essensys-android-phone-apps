## 1. Référentiel, cache et API (#39)

- [ ] 1.1 Copier le `catalog.json` canonique et les vecteurs communs `names_vectors.json`, et ajouter le test de parité du catalogue ; vérifier par un test qui reconstruit chaque libellé actuel de `IndexTable` à l'identique, et qui échoue quand une clé est modifiée dans la copie
- [ ] 1.2 Ajouter `NamesRepository` (appels `GET`, `PUT` et `DELETE /api/names` du mode Cloud et du mode Réseau local, `ETag`, `since`, `Mutex`, cache dans le `DataStore` effacé à la déconnexion, 404 vers les noms d'origine, 5xx qui garde les noms) ; vérifier par des tests MockWebServer (première lecture, 304, changements depuis une révision, 404, 5xx, cache relu au démarrage sans réseau, cache effacé à la déconnexion)
- [ ] 1.3 Implémenter la règle de nom affiché `displayName(kind, clé)` et la validation d'un nom sur les vecteurs communs ; vérifier par le passage de tous les vecteurs, y compris 40 et 41 caractères, balises et caractères de direction

## 2. Écran Noms et affichage (#40)

- [ ] 2.1 Ajouter l'écran « Noms » aux Réglages (catégories, origine, feuille de modification avec compteur, rétablissement, aperçu d'une pièce, erreurs du serveur conservant la saisie, refus hors ligne, masqué sans `can_rename`) ; vérifier par des tests UI (administrateur : renommer, rétablir, aperçu ; utilisateur ordinaire : écran absent ; nom refusé : texte conservé)
- [ ] 2.2 Remplacer par `displayName` les libellés de `IndexTable` lus par les écrans éclairage, volets, chauffage, prises de sécurité, chauffe-eau, arrosage, scénarios, éditeur de scénarios, accueil, confirmations, état et dernière action ; vérifier par un test UI qui renomme une pièce puis contrôle chacun de ces écrans, et par une recherche des usages restants de libellés d'origine
- [ ] 2.3 Rafraîchir les noms au démarrage, après la connexion, au retour au premier plan et toutes les 30 s écran visible ; vérifier par des tests (un nom modifié sur le serveur simulé apparaît en moins d'une minute, aucun appel quand l'app n'est pas visible, 304 sans effet sur l'écran)

## 3. Qualité et livraison (#41)

- [ ] 3.1 Ajouter `NR-android-24` (compte ordinaire : écran « Noms » absent et écriture refusée), `NR-android-25` (404 : noms d'origine et commandes actives) et `NR-android-26` (renommer ne change aucun indice ni masque), puis les scénarios sur émulateur en mode Cloud et en mode Réseau local, sans armoire réelle ; vérifier par leur passage dans les tests unitaires et instrumentés
- [ ] 3.2 CI verte, `/checkup` vert, captures de l'écran « Noms » et d'un écran renommé sur #41, README et version 2.5.0
- [ ] 3.3 Une fois les backends et les portails déployés : vérification réelle par un humain avec un compte administrateur (renommer une pièce, la voir sur le portail et l'app iPhone en moins d'une minute, la rétablir), puis release `android-v2.5.0`
