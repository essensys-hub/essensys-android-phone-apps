> Ticket : [essensys-hub/essensys-feature-lifecycle#39](https://github.com/essensys-hub/essensys-feature-lifecycle/issues/39). Feature ID : `session-30-jours-2026-11-001`.
> Feature répartie sur 3 dépôts, un change du même nom dans chacun : apps Android et iPhone, et backends (`essensys-user-portal-backend` et `essensys-server-backend`, change dans `essensys-memory`). Ce change ne décrit que le code de **essensys-android-phone-apps**.

## Why

Les utilisateurs doivent ressaisir leur mot de passe chaque jour : le jeton cloud expire après 24 h et la gateway ferme sa session après 7 jours d'inactivité, ou à son redémarrage. L'app garde déjà le jeton dans un stockage chiffré, mais cela ne sert à rien une fois le jeton expiré. Les utilisateurs demandent de ne pas avoir à retaper leur mot de passe pendant 30 jours.

## What Changes

- Case **« Se souvenir de moi (30 jours) »** sur l'écran de connexion, en mode Cloud et en mode Réseau local, décochée par défaut.
- Option cochée : le mot de passe n'est plus demandé pendant **30 jours fixes** à partir de la dernière saisie du mot de passe, même après fermeture de l'app ou redémarrage du téléphone. Passé ce délai, le mot de passe est redemandé.
- **Renouvellement transparent** de l'accès cloud : l'app échange sa session mémorisée contre un jeton de 24 h, sans rien demander à l'utilisateur.
- **Fin de la session mémorisée** par « Se déconnecter », par un changement ou une réinitialisation du mot de passe, ou par la désactivation du compte.
- Réglages : la date de fin de la session mémorisée est affichée.
- Option décochée : comportement actuel inchangé.
- Version 2.4.0, release `android-v2.4.0`.

## Capabilities

### New Capabilities

- `remember-me` : option « Se souvenir de moi », durée de 30 jours, renouvellement transparent, fin de session, stockage sécurisé.
- `remember-me-quality` : tests sans armoire, test NR de non-régression, checkup.

### Modified Capabilities

Aucune (les specs existantes de ce dépôt ne sont pas archivées sous `openspec/specs/`).

## Impact

- **Code** : `data/auth` (connexion avec option, renouvellement, fin de session), `data/session` (jeton mémorisé chiffré et date de fin), `data/http` (renouvellement après un refus 401), `ui/screens` (case à cocher, Réglages).
- **Serveur** : nouveaux comportements de `POST /api/auth/login` et nouvelle route de renouvellement côté cloud ; session de gateway durable côté réseau local. Décrits dans le change du même nom de `essensys-memory`. **Les apps ne sortent qu'après les backends.**
- **Compatibilité** : un serveur qui ignore l'option ne renvoie pas de session mémorisée ; l'app reste alors dans le comportement actuel.
- **Dépendances** : aucune nouvelle.
- **Suite** : #40 (déverrouillage biométrique) protégera l'usage de cette session ; hors périmètre ici.
