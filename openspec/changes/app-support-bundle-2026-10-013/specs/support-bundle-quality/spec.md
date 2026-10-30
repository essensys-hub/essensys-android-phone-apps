## Purpose

Garantit que le bouton Support et le bundle sont testés sans armoire réelle, qu'aucun secret ne peut fuiter dans le bundle, et que `/checkup` est vert avant la livraison.

## ADDED Requirements

### Requirement: Tests sans armoire
Les tests de essensys-android-phone-apps SHALL couvrir les tests de diagnostic (portail joignable, injoignable, session expirée, mode local, mode test), le contenu du zip et l'ouverture de l'email contre un backend simulé, garde no-armoire active ; aucune armoire réelle NE SHALL être pilotée.

#### Scenario: Suite de tests
- **WHEN** on lance les tests de essensys-android-phone-apps
- **THEN** ils passent sans accès réseau à une armoire réelle ni au vrai portail

### Requirement: Non-régression sur les secrets
Un test de non-régression `NR-android-20` SHALL produire un bundle avec un token, un cookie LAN, un mot de passe et une clé d'accès connus, puis vérifier qu'aucune de ces valeurs n'apparaît dans les fichiers du zip.

#### Scenario: Fuite détectée
- **WHEN** une modification ajoute un en-tête d'authentification ou un corps de requête au journal
- **THEN** `NR-android-20` échoue

### Requirement: Checkup
`/checkup` SHALL être vert sur essensys-android-phone-apps avant la PR, et un humain SHALL vérifier une fois l'envoi réel vers l'adresse support depuis un téléphone (Wi-Fi puis 4G).

#### Scenario: Vérification réelle
- **WHEN** un humain lance « Contacter le support » sur un téléphone réel et envoie l'email
- **THEN** l'adresse support reçoit le message avec le zip lisible
