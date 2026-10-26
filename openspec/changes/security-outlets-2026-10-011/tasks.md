## 1. Commande et état (#20)

- [ ] 1.1 Ajouter à `IndexTable` les indices 623 et 440, la commande `[590 = 1, 623 = 1|2]` et le décalage 31 des scénarios ; vérifier par `NR-android-18` (couples exacts, 664 pour le scénario 2)
- [ ] 1.2 Ajouter au `ComfortRepository` la commande des prises (lot `injectBatch`) et la lecture de 440 ; vérifier par des tests MockWebServer en cloud et en LAN (dry-run compris)

## 2. Écran Prises de sécurité (#21)

- [ ] 2.1 Écran poussé depuis une tuile d'accueil : état, « Couper » avec confirmation, « Rétablir », relecture de l'état, désactivé hors ligne ; vérifier par des tests instrumentés (annuler n'envoie rien, hors ligne désactivé, état inconnu)
- [ ] 2.2 Choix « Prises de sécurité » dans l'éditeur de scénarios ; vérifier par un test instrumenté (indice 664 enregistré à 1 pour « Je sors »)

## 3. Qualité et livraison (#22)

- [ ] 3.1 CI verte, `/checkup` vert, captures de l'écran sur #36, README et version 2.2.0
- [ ] 3.2 Validation sur le banc BP par un humain (coupure, éclairage sans recoupure, rétablissement), puis release `android-v2.2.0`
