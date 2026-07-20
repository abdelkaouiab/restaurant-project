# Exigences de sécurité

Ce document constitue la baseline de sécurité. Chaque fonctionnalité future devra identifier les exigences applicables et les couvrir par configuration, code et tests.

## Secrets et configuration

- Aucun mot de passe, jeton, cookie, certificat privé ou secret ne doit être commité.
- Les secrets proviennent de variables d'environnement ou d'un gestionnaire de secrets en production.
- `.env.example` contient uniquement des noms de variables et des valeurs fictives non sensibles.
- `.env`, variantes locales, clés privées, keystores et fichiers d'IDE sont ignorés par Git.
- Les profils de production refusent de démarrer si une configuration critique manque ou utilise une valeur d'exemple.

## Authentification

- Hacher les mots de passe avec BCrypt ou Argon2 et un paramétrage maintenable ; ne jamais les chiffrer de façon réversible.
- Mettre en place une politique de mot de passe, une limitation des tentatives et un comportement qui évite l'énumération des comptes.
- Invalider ou renouveler les sessions/identifiants après authentification et changement de privilèges.
- Si l'authentification utilise des cookies : `HttpOnly`, `Secure` en production, `SameSite` adapté, portée minimale et protection CSRF active.
- Ne jamais stocker un secret durable dans `localStorage`.

## Autorisation

- Refuser par défaut toute route non explicitement publique.
- Vérifier les permissions dans les services ou via `@PreAuthorize`, pas seulement dans l'interface.
- Combiner le rôle avec le périmètre ou la propriété de la ressource afin de prévenir l'IDOR.
- Retourner `401` pour une authentification requise/invalide et `403` pour une identité authentifiée mais non autorisée, sans révéler de détail sensible.
- Journaliser les changements de rôle, permissions et opérations critiques sans donnée secrète.

## Entrées, sorties et API

- Utiliser des DTO dédiés avec Jakarta Bean Validation et des listes de champs autorisés.
- Prévenir le mass assignment : aucun binding direct vers une entité.
- Encoder les sorties dans leur contexte et ne pas rendre de HTML fourni par l'utilisateur sans assainissement adapté.
- Utiliser JPA ou des requêtes paramétrées ; aucune concaténation de valeur utilisateur dans du SQL.
- Limiter les champs de tri à une liste blanche et paginer les listes.
- Limiter la taille des requêtes, collections, chaînes et fichiers.
- Restreindre CORS aux origines, méthodes et en-têtes nécessaires ; ne jamais utiliser une origine générique avec des credentials.
- Renvoyer des erreurs structurées sans stack trace, chemin interne, SQL ou secret.

## HTTP et transport

- Exiger HTTPS en production et configurer HSTS lorsque la terminaison TLS est maîtrisée.
- Ajouter au minimum des politiques adaptées pour `Content-Security-Policy`, `X-Content-Type-Options`, anti-framing et `Referrer-Policy`.
- Désactiver les endpoints et méthodes inutiles.
- Ne publier la documentation ou les outils d'administration en production qu'avec une autorisation explicite.

## Données, transactions et concurrence

- Calculer tous les montants côté serveur avec `BigDecimal` et des règles d'arrondi explicites.
- Copier le prix dans la ligne de commande pour préserver l'historique.
- Encadrer les changements d'état par des transactions et transitions autorisées.
- Employer le verrouillage optimiste avec `@Version` lorsqu'une perte de mise à jour est possible.
- Garantir l'unicité de la consommation de stock et de la réception d'achat par des protections persistantes et applicatives.
- Prévenir les doubles paiements et traitements par idempotence.
- Ne générer une facture qu'après validation atomique du paiement.
- Prévenir les conflits de réservation, y compris sous concurrence.
- Préférer la désactivation (`actif`) à la suppression des données historiques ; protéger les suppressions par autorisation et règles métier.

## Journalisation et confidentialité

- Journaliser l'identité technique, l'action, la cible, le résultat, la date et un identifiant de corrélation pour les opérations sensibles.
- Ne jamais journaliser mot de passe, jeton, cookie, secret, numéro de carte complet ou corps sensible.
- Contrôler l'accès aux journaux, leur rétention et leur intégrité.
- Éviter les données personnelles non nécessaires et définir leur durée de conservation.

## Dépendances et exploitation

- Épingler des versions compatibles et maintenues ; examiner les mises à jour avant intégration.
- Ajouter une analyse de vulnérabilités des dépendances au processus de vérification.
- Ne pas embarquer de compte ou secret par défaut.
- Séparer les configurations de développement, test et production.
- Utiliser un compte PostgreSQL à privilèges minimaux et des sauvegardes testées.
- Protéger et restreindre les endpoints de santé, métriques et administration.

## Tests de sécurité minimaux

Pour chaque ressource protégée, tester au minimum :

- accès sans authentification : `401` ;
- rôle insuffisant : `403` ;
- rôle autorisé : succès ;
- accès à l'identifiant d'une ressource hors périmètre : refus ;
- entrée invalide et champ inattendu : rejet ;
- transition d'état interdite : rejet ;
- concurrence ou répétition sur une opération à effet unique : un seul effet ;
- erreur interne : aucune information sensible exposée.

La baseline sera affinée avec le modèle d'authentification et l'environnement de déploiement, qui ne sont pas encore choisis.
