# Audit de sécurité — étape 14

Date de l'audit : 25 août 2026  
Périmètre : `backend/`, `frontend/`, migrations Flyway, configuration, dépendances et tests.  
Conclusion : aucun problème **CRITIQUE** identifié. Trois problèmes **ELEVEE** et cinq problèmes **MOYENNE** ont été corrigés.

## Méthode

L'audit a combiné une revue des contrôleurs, services, entités, dépôts, DTO, filtres Spring Security, migrations SQL, appels réseau React, routes frontend, configurations et tests. Des recherches systématiques ont été faites sur les requêtes dynamiques, sorties HTML dangereuses, stockage navigateur, secrets, logs, fichiers, points d'administration et mécanismes de concurrence.

Les niveaux signifient :

- **CRITIQUE** : compromission directe et large, exploitable immédiatement ;
- **ELEVEE** : atteinte importante à l'authentification, aux données ou à la disponibilité ;
- **MOYENNE** : protection incomplète nécessitant des conditions supplémentaires ;
- **FAIBLE** : durcissement ou exposition limitée.

## Problèmes détectés et corrigés

| Réf. | Niveau | Risque | Fichier(s) | Correction | Test ajouté / preuve |
|---|---|---|---|---|---|
| SEC-01 | **ELEVEE** | Une session déjà ouverte conservait ses anciennes permissions après modification du rôle, du mot de passe, du statut utilisateur ou du statut employé. | `security/RestaurantUserPrincipal.java`, `security/RestaurantUserDetailsService.java`, `security/SessionSecurityValidationFilter.java`, `config/SecurityConfig.java` | Ajout d'un état de sécurité versionné au principal et validation serveur à chaque requête authentifiée. Toute divergence invalide immédiatement la session et renvoie une erreur 401 générique. | `SessionSecurityValidationFilterTest` : session inchangée acceptée ; session périmée invalidée sans exposer la cause. |
| SEC-02 | **ELEVEE** | Une image constituée uniquement de quelques octets de signature PNG/JPEG/WebP pouvait être acceptée. Cela permettait de stocker un fichier tronqué ou malformé et ne limitait pas les dimensions décompressées. | `service/ProduitImageStorageService.java`, `application.yml`, `.env.example` | Décodage complet PNG/JPEG, validation de fin de fichier, cohérence MIME/format, validation structurelle RIFF/WebP et plafond de 12 millions de pixels. Les noms restent générés côté serveur et le chemin reste normalisé. | `ProduitImageStorageServiceTest` : image décodable acceptée, fausse signature et MIME incohérent refusés. Le test d'intégration utilise maintenant un PNG complet. |
| SEC-03 | **ELEVEE** | `nanoid 3.3.16`, dépendance transitive frontend, était affectée par GHSA-2v37-7h3g-55p8 (boucle infinie possible dans un générateur personnalisé). | `frontend/package-lock.json` | Mise à jour du verrouillage npm vers une version corrigée par `npm audit fix`, sans changement majeur applicatif. | `npm audit --audit-level=high` : 1 vulnérabilité élevée avant correction, 0 après correction. |
| SEC-04 | **MOYENNE** | Actuator était accessible à tout utilisateur authentifié et Swagger n'avait pas de restriction de rôle explicite. Swagger restait activé par défaut en production. | `config/SecurityConfig.java`, `application-prod.yml` | Actuator et Swagger sont réservés au rôle `ADMINISTRATEUR`; Swagger/OpenAPI est totalement désactivé sous le profil `prod`; Actuator n'expose que `health` et écoute par défaut sur `127.0.0.1:8081`. | `HealthControllerTest` vérifie 401 sans session et 403 pour un non-administrateur. `ProductionSecurityConfigurationTest` vérifie la désactivation de Swagger et l'adresse de management. |
| SEC-05 | **MOYENNE** | Il n'existait pas de profil de production imposant des valeurs obligatoires pour la base, CORS, le stockage des images et les cookies HTTPS. Une mauvaise variable locale pouvait affaiblir le déploiement. | `application-prod.yml`, `.env.example` | Ajout d'un profil `prod` sans valeur par défaut pour les secrets et origines, cookie `Secure`, `HttpOnly`, `SameSite=Strict`, session 15 minutes, Flyway clean désactivé, erreurs sûres et logs limités. | `ProductionSecurityConfigurationTest` vérifie les propriétés sensibles et l'absence de valeur CORS de secours. |
| SEC-06 | **MOYENNE** | Les réponses ne déclaraient pas toutes les politiques de navigateur utiles. | `config/SecurityConfig.java` | CSP d'API restrictive, HSTS un an avec sous-domaines et preload, `Permissions-Policy`, `Referrer-Policy`, anti-framing et `nosniff` via Spring Security. | `HealthControllerTest` vérifie CSP, `X-Content-Type-Options` et `Permissions-Policy`. |
| SEC-07 | **MOYENNE** | Certains identifiants, recherches et paramètres de pagination des achats/fournisseurs/journal n'avaient pas de contraintes HTTP explicites. Des tailles négatives ou excessives pouvaient provoquer des erreurs ou une charge inutile. | `controller/AchatController.java`, `controller/TableauBordController.java`, DTO achat/réception | Ajout de `@Validated`, `@Positive`, `@Min`, `@Max` et `@Size`; les requêtes invalides sont traduites en réponse 400 générique. | `SecurityInputValidationTest` vérifie les identifiants non positifs, les chaînes trop longues et la pagination hors limites. |
| SEC-08 | **MOYENNE** | Le backend ne disposait pas d'un contrôle SCA bloquant dans le cycle Maven. | `backend/pom.xml`, `frontend/package.json` | Ajout d'OWASP Dependency-Check 12.2.2 sur la phase `verify`, seuil d'échec CVSS 7, rapports HTML/JSON et clé NVD via environnement. Ajout du script npm `audit:security`. | Exécution npm réussie. L'exécution OWASP a été tentée mais la NVD a répondu HTTP 429 sans clé ; elle doit être rejouée en CI avec `NVD_API_KEY`. |

## Contrôles vérifiés sans nouvelle anomalie

| Domaine demandé | État vérifié |
|---|---|
| Authentification | BCrypt coût 12, message générique, limitation des échecs par e-mail/IP, rotation de l'identifiant de session, journalisation sans mot de passe. |
| Autorisations | Refus par défaut dans Spring Security et `@PreAuthorize` sur les frontières de service. Permissions fines par module. |
| IDOR | Les serveurs ne peuvent lire ou modifier que leurs propres commandes, sauf rôle de supervision. Les autres ressources exigent une permission métier dédiée. |
| CSRF | Cookie `XSRF-TOKEN` associé à l'en-tête `X-XSRF-TOKEN`; toutes les écritures sont protégées, y compris connexion et déconnexion. |
| CORS | Origines explicites seulement, joker refusé au démarrage, credentials limités à ces origines, méthodes et en-têtes en liste blanche. |
| XSS | React échappe le contenu ; aucune utilisation de `dangerouslySetInnerHTML`, `innerHTML`, `eval` ou `document.write`. CSP et `nosniff` complètent la défense. |
| Injections SQL | Requêtes JPA paramétrées et `JdbcTemplate` avec paramètres positionnels. Les champs de tri sont traduits par listes blanches. Aucune concaténation d'entrée dans le SQL. |
| Validation / mass assignment | DTO d'entrée dédiés, Bean Validation, champs calculés annotés `@Null`, prix/totaux recalculés serveur et Jackson refuse les propriétés inconnues. Aucune entité JPA exposée directement. |
| Sessions et cookies | Cookie de session `HttpOnly`, `Secure` en production, `SameSite=Strict`, suivi cookie uniquement, rotation à la connexion, révocation après modification de sécurité. Aucun jeton stocké dans `localStorage`. |
| Secrets | Mot de passe DB obligatoire, `.env` et clés/certificats ignorés, aucune clé réelle trouvée dans les sources. `.env.example` ne contient que des exemples. |
| Erreurs | Réponses 400/401/403/404/409/429/500 structurées et génériques ; aucune stack trace, classe SQL ou exception interne exposée. |
| Logs et données personnelles | Aucun mot de passe, jeton ou cookie journalisé. La réponse publique du journal exclut `details` et `adresseIp`. Les réponses d'authentification excluent le hash. |
| Fichiers | Taille, MIME réel, structure, dimensions, nom aléatoire, chemin normalisé et suppression transactionnelle contrôlés. |
| Transitions d'état | Transitions encapsulées dans les entités/services ; confirmation vide refusée ; paiement seulement depuis `PRETE`; facture seulement après paiement complet. |
| Calculs métier | Prix copié depuis le produit, sous-totaux/totaux calculés côté serveur, montants client calculés explicitement refusés. |
| Concurrence | `@Version`, verrous pessimistes et contraintes uniques protègent commandes, achats, stocks, paiements et réservations. Les conflits deviennent HTTP 409. |
| Doubles paiements | Clé d'idempotence unique, verrou sur commande, contrôle du restant et facture unique par commande. |
| Doubles déductions | Unicité `(commande_id, ingredient_id)` et vérification avant déduction ; transaction annulée en cas de conflit. |
| Doubles réceptions | Clé d'idempotence unique, signature de requête, verrou achat et unicité mouvement/réception/ingrédient. |
| Réservations simultanées | Verrou pessimiste sur la table pendant la détection de chevauchement et contrainte unique pour la commande issue d'une réservation. |

## Vérifications exécutées

- `cd backend && ./mvnw -DskipTests package` : succès.
- `cd backend && ./mvnw -Dtest=SessionSecurityValidationFilterTest,ProductionSecurityConfigurationTest,ProduitImageStorageServiceTest test` : **6 tests, 0 échec**.
- `cd backend && ./mvnw -Dtest=SecurityInputValidationTest test` : **2 tests, 0 échec**.
- `cd backend && ./mvnw -Dtest=HealthControllerTest test` : **5 tests, 0 échec**.
- `cd backend && ./mvnw test` avant corrections : **96 tests, 0 échec, 88 ignorés**. Les 88 tests Testcontainers ont été ignorés car Docker n'était pas accessible dans l'environnement d'audit.
- `cd frontend && npm run typecheck` : succès.
- `cd frontend && npm run lint` : succès.
- `cd frontend && npm run build` : succès.
- `cd frontend && npm run audit:security` : **0 vulnérabilité après correction**.
- `cd frontend && npm test` : **96 tests réussis, 0 échec**. Les requêtes de labels imbriqués, les assertions de focus/valeur et le pool Vitest sont maintenant stabilisés.
- `cd backend && ./mvnw -DskipTests org.owasp:dependency-check-maven:12.2.2:check` : téléchargement du plugin réussi, analyse non terminée à cause de réponses NVD HTTP 429 et d'une base locale vide. Rejouer avec une clé NVD.

## Limites restantes

- La limitation des connexions est en mémoire locale. Pour plusieurs instances, utiliser un compteur partagé (Redis ou passerelle) afin d'éviter le contournement par répartition des requêtes.
- Le stockage d'images est local. En production multi-instance, employer un stockage objet privé avec analyse antivirus et politique de rétention.
- Les tests de concurrence PostgreSQL doivent être rejoués avec Docker ou une base éphémère accessible avant livraison.
- Le résultat OWASP Dependency-Check n'est pas conclusif tant que la CI ne l'a pas exécuté avec `NVD_API_KEY`.
