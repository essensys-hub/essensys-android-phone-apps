## Context

Voir `proposal.md` (Why). État actuel :

- Cloud : `POST /api/auth/login` renvoie un JWT de 24 h, sans état. `POST /api/auth/logout` ne fait rien. Rien ne permet de révoquer un jeton.
- Réseau local : la gateway garde ses sessions **en mémoire** (7 jours, glissants) ; un redémarrage de la gateway ferme toutes les sessions.
- L'app stocke déjà le jeton et le cookie chiffrés (`PersistentSessionStore` + `KeystoreSecretCipher`), exclut ses données des sauvegardes (`allowBackup="false"`) et n'a jamais stocké le mot de passe depuis la 2.0.
- Sur un refus 401, `AuthRepository.onApiError` efface toute la session.

## Goals / Non-Goals

**Goals:**
- 30 jours fixes sans mot de passe, cloud et réseau local, par appareil.
- Session révocable : déconnexion, changement de mot de passe, compte désactivé.
- Aucun secret supplémentaire exposé (journal, bundle Support, sauvegardes).

**Non-Goals:**
- Déverrouillage biométrique de l'app (#40).
- Liste des appareils connectés et révocation depuis le portail (évolution possible, non nécessaire ici).
- Durée réglable par l'utilisateur ou par l'administrateur.
- Mémoriser la connexion des comptes Google et Apple (non disponibles dans l'app).

## Decisions

**1. Session mémorisée distincte du jeton d'accès.** Le serveur émet un `remember_token` opaque (256 bits aléatoires), stocké **haché** côté serveur, avec une date de fin fixée à la connexion (+30 jours). L'app l'échange contre un JWT de 24 h quand l'accès expire. *Alternatives écartées :* JWT de 30 jours (impossible à révoquer par appareil, un changement de mot de passe ne le coupe pas) ; session glissante (l'utilisateur ne retaperait jamais son mot de passe, contraire à la demande).

**2. Contrat serveur** (implémenté par le change `essensys-memory` du même nom, **avant** les apps) :

| Mode | Appel | Évolution |
|---|---|---|
| Cloud | `POST /api/auth/login` | corps : `remember_me` (booléen, optionnel). Réponse : en plus `remember_token` et `remember_expires_at` (RFC 3339 UTC) si l'option est demandée et prise en charge |
| Cloud | `POST /api/auth/remember/refresh` | corps `{ "remember_token": "…" }` ; 200 : `token` (JWT 24 h), `user`, `password_change_required` ; 401 `remember_invalid` (inconnue, expirée ou révoquée) ; 403 `account_forbidden` |
| Cloud | `POST /api/auth/logout` | corps optionnel `{ "remember_token": "…" }` : révoque cette session |
| Réseau local | `POST /api/auth/login` | corps : `remember_me`. Le cookie de session dure 30 jours fixes et survit au redémarrage de la gateway. Réponse JSON : `remember_expires_at` |

La date de fin affichée est celle **renvoyée par le serveur**, pas calculée sur le téléphone (écart d'horloge).

**3. Stockage sur le téléphone.** `SessionState` reçoit `rememberToken` (chiffré comme `token`) et `rememberExpiresAt`. Le mot de passe n'est jamais lu en dehors de l'appel de connexion. Le jeton mémorisé est masqué dans les mêmes filtres que le jeton et le cookie (journal des requêtes, bundle Support).

**4. Renouvellement paresseux, une seule fois.** Sur un 401 cloud avec un `rememberToken` valide, l'app appelle `remember/refresh` derrière un verrou unique, puis rejoue la requête **une fois**. Si le jeton d'accès a déjà changé pendant l'attente (autre requête passée avant), elle rejoue sans redemander. Un échec du renouvellement efface la session, comme aujourd'hui. *Alternative écartée :* renouvellement préventif selon l'`exp` du JWT (plus de code, aucun gain visible).

**5. Réseau local.** Pas de jeton supplémentaire : la gateway porte la durée de 30 jours sur son cookie. L'app ne conserve que la date de fin. Un 401 efface la session, comme aujourd'hui.

**6. Connexion.** `AuthRepository.login` reçoit `rememberMe`. Case décochée par défaut, non mémorisée d'une connexion à l'autre (choix prudent : un téléphone prêté ne garde pas la session par erreur).

**7. Changement de mot de passe.** Le serveur révoque **toutes** les sessions mémorisées de l'utilisateur, y compris celle du téléphone qui change le mot de passe : l'app redemande donc le mot de passe, comme le fait déjà la gateway.

## Risks / Trade-offs

- **Téléphone volé et déverrouillé** : accès pendant 30 jours au plus → chiffrement Keystore, révocation par changement de mot de passe, déconnexion ; #40 ajoutera le déverrouillage biométrique. Un écran « appareils connectés » reste possible plus tard.
- **Applications sorties avant les backends** → les apps ne sortent qu'après les backends ; sans prise en charge serveur l'app se replie sur le comportement actuel (spec « Serveur sans prise en charge »).
- **Écart d'horloge du téléphone** → l'autorité est le serveur ; l'horloge locale ne sert qu'à masquer la date et à éviter un appel inutile.
- **Renouvellements en rafale** → verrou unique côté app, limitation de débit côté serveur.
- **Anciennes versions de l'app** : non concernées, elles n'envoient jamais `remember_me`.

## Migration Plan

1. Backends déployés d'abord (cloud puis gateway), sans effet visible tant que les apps n'envoient pas `remember_me`.
2. Apps 2.4.0 : le jeton mémorisé n'existe qu'après une nouvelle connexion avec la case cochée ; les sessions actuelles continuent de fonctionner.
3. Retour arrière : désactiver côté serveur ; les apps retombent sur le comportement actuel sans migration.
