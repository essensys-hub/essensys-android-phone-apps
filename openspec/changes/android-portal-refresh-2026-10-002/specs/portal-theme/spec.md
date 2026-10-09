## Purpose

Donne à l'app Android l'apparence du portail Essensys actuel (couleurs, surfaces, rayons, composants) en clair et en sombre, pour une expérience cohérente entre web et mobile.

## ADDED Requirements

### Requirement: Tokens de couleur du portail
L'app SHALL utiliser les tokens du portail (`essensys-user-portal-frontend/src/index.css`) : clair — primary #2563eb, primary-dark #1d4ed8, secondary #64748b, danger #dc2626, success #16a34a, warning #f59e0b, fond #f8f8f8, carte #ffffff, en-tête de carte #f9fafb, bordure #e2e8f0, texte #111827, texte secondaire #6b7280 ; sombre — primary #3b82f6, primary-dark #60a5fa, secondary #94a3b8, fond #0f172a, carte #1e293b, en-tête #334155, bordure #475569, texte #f1f5f9, texte secondaire #94a3b8. Aucune couleur NE SHALL être codée en dur hors du thème.

#### Scenario: Mode sombre système
- **WHEN** le téléphone est en thème sombre et le réglage de l'app est « Système »
- **THEN** l'app affiche le fond #0f172a et les cartes #1e293b

### Requirement: Choix du thème
Les réglages SHALL proposer Système / Clair / Sombre, persistés localement.

#### Scenario: Forcer le clair
- **WHEN** l'utilisateur choisit « Clair » alors que le système est sombre
- **THEN** l'app reste en thème clair après redémarrage

### Requirement: Composants alignés
Les cartes de commande SHALL reprendre la forme du portail (coins arrondis ~12 dp, en-tête de carte, bordure), les boutons ~8 dp et les badges d'état en pilule ; les écrans SHALL rester utilisables de 360 dp de large à la tablette.

#### Scenario: Petit écran
- **WHEN** l'app est affichée sur un écran de 360 dp de large
- **THEN** aucun contenu n'est tronqué ni ne nécessite de défilement horizontal
