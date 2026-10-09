## Purpose

Authentifie l'utilisateur de l'app Android avec les mêmes comptes et mécanismes que le portail Essensys, en cloud (JWT) comme en LAN (session gateway), sans jamais stocker de secret en clair.

## ADDED Requirements

### Requirement: Connexion cloud par email et mot de passe
En mode cloud, l'app SHALL authentifier l'utilisateur via `POST https://mon.essensys.fr/api/auth/login` (`{email, password}`) et utiliser le JWT retourné en en-tête `Authorization: Bearer` sur tous les appels `/api/portal/*`. L'app NE SHALL PAS utiliser le Basic Auth ni appeler `/api/admin/*` en mode cloud.

#### Scenario: Connexion réussie
- **WHEN** l'utilisateur saisit des identifiants valides en mode cloud
- **THEN** l'app reçoit un JWT, l'enregistre de façon sécurisée et affiche l'accueil

#### Scenario: Identifiants invalides
- **WHEN** le serveur répond 401 au login
- **THEN** l'app affiche « Email ou mot de passe incorrect » sans révéler lequel est faux et reste sur l'écran de connexion

### Requirement: Connexion LAN
En mode LAN, l'app SHALL s'authentifier auprès de la gateway (`POST https://mon.essensys.local/api/auth/login`) et conserver le cookie de session `essensys_lan_session` pour les appels `/api/admin/*` et `/api/scenarios/*` de la gateway.

#### Scenario: Session LAN
- **WHEN** l'utilisateur se connecte en mode LAN avec des identifiants valides
- **THEN** les appels suivants à la gateway portent le cookie de session et réussissent sans nouvelle saisie

#### Scenario: Changement de mot de passe en LAN
- **WHEN** l'utilisateur change son mot de passe en mode LAN (`PUT /api/user/me/password`)
- **THEN** l'app l'informe que la gateway ferme toutes ses sessions et le renvoie à l'écran de connexion

### Requirement: Stockage sécurisé
Le jeton et le cookie de session SHALL être stockés chiffrés (clé Android Keystore). Le mot de passe NE SHALL JAMAIS être persisté. Les anciennes préférences en clair (`EssensysPrefs`) SHALL être supprimées à la première exécution de la nouvelle version.

#### Scenario: Migration depuis la v1.0.0
- **WHEN** la nouvelle version démarre sur un appareil ayant des identifiants v1.0.0 en clair
- **THEN** ces préférences sont effacées et l'utilisateur est invité à se reconnecter

### Requirement: Expiration et révocation
Sur réponse 401 d'une route authentifiée (JWT expiré après 24 h, session LAN invalide, `temporary_password_expired`) ou 403 `account_forbidden` / `account_disabled`, l'app SHALL effacer la session et revenir à l'écran de connexion avec un message adapté. Les corps d'erreur SHALL être lus en JSON (`error`, `message`) avec repli sur le texte brut.

#### Scenario: JWT expiré
- **WHEN** un appel `/api/portal/*` répond 401
- **THEN** la session est effacée et l'écran de connexion s'affiche avec « Session expirée, reconnectez-vous »

### Requirement: Changement de mot de passe obligatoire
En cloud, quand la réponse de login porte `password_change_required: true`, ou qu'une route authentifiée répond `409` avec `error = password_change_required`, l'app SHALL afficher un écran de changement de mot de passe qui appelle `POST /api/auth/password/change` (`current_password`, `new_password`, 8 caractères minimum), en conservant le même jeton. L'app NE SHALL autoriser aucune autre action avant succès. Les erreurs `weak_password`, `password_reused` et `invalid_current_password` SHALL être affichées en clair.

#### Scenario: Mot de passe temporaire
- **WHEN** l'utilisateur se connecte avec un mot de passe temporaire (login 200 avec `password_change_required: true`)
- **THEN** l'app affiche l'écran de changement de mot de passe et, après succès, l'accueil

#### Scenario: Verrou posé pendant la session
- **WHEN** un appel `/api/portal/*` répond 409 `password_change_required`
- **THEN** l'app affiche l'écran de changement de mot de passe sans perdre la session

### Requirement: Déconnexion
L'app SHALL proposer une déconnexion qui efface jeton, cookie et données de session locales.

#### Scenario: Déconnexion
- **WHEN** l'utilisateur choisit « Se déconnecter »
- **THEN** aucune donnée de session ne subsiste et l'écran de connexion s'affiche
