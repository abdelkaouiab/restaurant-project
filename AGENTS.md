# Consignes de travail dans ce dépôt

Ces règles s'appliquent à toute intervention future dans le dépôt.

## Méthode obligatoire

1. Inspecter entièrement les fichiers concernés et l'état Git avant toute modification.
2. Décrire brièvement l'état existant et les changements prévus.
3. Implémenter uniquement l'étape explicitement demandée, sans anticiper la suivante.
4. Préserver le code existant et justifier toute suppression.
5. Éviter les duplications et les dépendances sans utilité démontrée.
6. Ajouter ou adapter les tests proportionnellement au changement.
7. Exécuter les vérifications disponibles et rapporter honnêtement les résultats.
8. Fournir les commandes de compilation, test et lancement pertinentes.
9. Terminer par une checklist de vérification manuelle et les limites restantes.

Ne jamais mélanger une étape backend et la création du frontend sauf demande explicite.

## Architecture

- Utiliser Java 21 et Spring Boot 3.x pour le backend.
- Maintenir des couches `controller`, `dto`, `entity`, `enums`, `exception`, `mapper`, `repository`, `security`, `service`, `validation` et `config`.
- Garder les règles métier dans les services, jamais dans les contrôleurs.
- Ne jamais exposer une entité JPA dans l'API ; utiliser des DTO d'entrée et de sortie dédiés.
- Valider les entrées avec Jakarta Bean Validation, puis appliquer les invariants métier dans les services.
- Centraliser la traduction des exceptions en réponses API sûres.
- Utiliser Flyway comme source de vérité du schéma et ne pas compter sur la génération automatique en production.
- Utiliser des transactions aux frontières métier et `@Version` lorsque des écritures concurrentes sont possibles.

## Règles métier transversales

- Calculer prix, sous-totaux et totaux côté serveur.
- Ne jamais faire confiance aux champs calculés transmis par un client.
- Exiger au moins une ligne avant de confirmer une commande.
- Copier le prix courant du produit dans la ligne de commande.
- Ne pas supprimer physiquement une commande confirmée ou payée.
- Contrôler explicitement toutes les transitions d'état.
- Générer une facture seulement après un paiement valide.
- Garantir l'idempotence de la consommation de stock et de la réception d'achat.
- Empêcher les conflits de réservation.
- Préserver l'historique par désactivation via `actif` lorsque le domaine l'exige.

## Sécurité et qualité

- Appliquer un refus d'accès par défaut et vérifier rôles, permissions et propriété des ressources côté serveur.
- Prévenir l'IDOR : ne jamais conclure qu'une ressource est autorisée parce que son identifiant est connu.
- Utiliser BCrypt ou Argon2 pour les mots de passe ; ne jamais journaliser mots de passe, jetons, cookies ou secrets.
- Ne coder aucun secret en dur. Les valeurs locales vont dans un fichier `.env` ignoré ; seules des clés fictives documentées vont dans `.env.example`.
- Restreindre CORS, listes de champs de tri et données modifiables par DTO.
- Employer des requêtes paramétrées/JPA, une pagination et des erreurs sans détail sensible.
- Configurer CSRF selon le mécanisme d'authentification, des cookies sûrs en production, des en-têtes de sécurité et une limitation des connexions.
- Tester les accès autorisés ainsi que les réponses `401` et `403`.
- Vérifier régulièrement les vulnérabilités des dépendances.

Consulter `docs/architecture.md` et `docs/security-requirements.md` avant toute décision structurante ou liée à la sécurité.
