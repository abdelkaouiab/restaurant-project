# Déploiement sécurisé

Ce document décrit le déploiement de production attendu après l'étape 14. Le profil `prod` refuse de démarrer si les paramètres sensibles essentiels ne sont pas fournis.

## 1. Construire et contrôler les dépendances

Backend :

```bash
cd backend
export NVD_API_KEY='valeur-fournie-par-la-NVD'
./mvnw clean verify
```

`verify` exécute les tests et OWASP Dependency-Check. La construction échoue à partir d'un CVSS de 7. Les rapports sont produits dans `backend/target.nosync/dependency-check-report.html` et `.json`. Le suffixe `.nosync` empêche iCloud d'externaliser les classes compilées lorsque le dépôt se trouve dans le dossier Documents de macOS.

Frontend :

```bash
cd frontend
npm ci
npm run audit:security
npm run typecheck
npm run lint
npm test
npm run build
```

Ne pas livrer si une vulnérabilité élevée/critique n'est pas corrigée ou formellement acceptée avec justification et date d'expiration.

## 2. Variables obligatoires

Fournir les secrets au processus via un coffre de secrets, jamais dans Git, une image Docker ou une commande visible dans l'historique shell.

```text
SPRING_PROFILES_ACTIVE=prod
DB_HOST=db.internal
DB_PORT=5432
DB_NAME=restaurant
DB_USERNAME=restaurant_app
DB_PASSWORD=<secret unique>
APP_CORS_ALLOWED_ORIGINS=https://restaurant.example.com
APP_PRODUCT_IMAGES_DIRECTORY=/var/lib/restaurant/product-images
APP_RESTAURANT_TIME_ZONE=Africa/Casablanca
MANAGEMENT_SERVER_ADDRESS=127.0.0.1
MANAGEMENT_SERVER_PORT=8081
```

Les valeurs suivantes peuvent être adaptées sans diminuer les plafonds de sécurité :

```text
APP_SESSION_TIMEOUT=15m
APP_BCRYPT_STRENGTH=12
APP_LOGIN_MAX_ATTEMPTS=5
APP_LOGIN_ATTEMPT_WINDOW=15m
APP_PRODUCT_IMAGE_MAX_SIZE=5MB
APP_PRODUCT_IMAGE_MAX_REQUEST_SIZE=6MB
APP_PRODUCT_IMAGE_MAX_BYTES=5242880
APP_PRODUCT_IMAGE_MAX_PIXELS=12000000
```

Ne jamais activer `APP_COOKIE_SECURE=false` avec le profil `prod`. Le profil force déjà `Secure=true`.

## 3. TLS et reverse proxy

- Exposer uniquement HTTPS 443 et rediriger HTTP vers HTTPS.
- Utiliser TLS 1.2 minimum, de préférence TLS 1.3, avec certificats renouvelés automatiquement.
- Supprimer les en-têtes `Forwarded` et `X-Forwarded-*` fournis par le client avant d'ajouter ceux du proxy de confiance.
- Limiter la taille des corps au même plafond que Spring (`6MB`) et appliquer des délais de lecture/écriture.
- Ne jamais mettre en cache `/api/auth/**`, les réponses personnelles ou les PDF de facture.
- Le port de management 8081 reste sur loopback ou sur un réseau d'administration non routable depuis Internet.

Pour le frontend statique, configurer au minimum :

```text
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self' https://api.restaurant.example.com; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'; upgrade-insecure-requests
Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
X-Content-Type-Options: nosniff
Referrer-Policy: no-referrer
Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=(), usb=()
```

Adapter seulement `connect-src` au domaine réel de l'API. Ne pas ajouter `unsafe-inline` ou `unsafe-eval`.

## 4. Base PostgreSQL

- Créer un utilisateur applicatif distinct, sans droits de superutilisateur, création de base ou création de rôle.
- Autoriser uniquement connexion, lecture/écriture sur le schéma applicatif et usage des séquences nécessaires.
- Exécuter Flyway avec un compte de migration séparé si l'organisation le permet.
- Chiffrer les sauvegardes, tester leur restauration et fixer une durée de rétention.
- Restreindre PostgreSQL au réseau privé et exiger TLS entre application et base si le réseau n'est pas entièrement maîtrisé.
- Ne jamais exécuter `flyway clean` en production ; le profil le désactive.

## 5. Sessions, CSRF et CORS

- Le navigateur doit recevoir `RESTAURANT_SESSION` avec `HttpOnly`, `Secure`, `SameSite=Strict` et `Path=/`.
- Le cookie `XSRF-TOKEN` est lisible par le frontend uniquement pour recopier sa valeur dans `X-XSRF-TOKEN`; il ne doit jamais être journalisé.
- `APP_CORS_ALLOWED_ORIGINS` contient des origines complètes séparées par virgules, sans chemin et sans joker.
- Après changement de mot de passe, rôle, statut utilisateur ou employé, la prochaine requête invalide la session existante.
- Pour plusieurs instances, utiliser des sessions partagées Spring Session/Redis et un limiteur de connexion partagé, ou garantir l'affinité tout en conservant une révocation centralisée.

## 6. Actuator et Swagger

- Swagger/OpenAPI est désactivé en production. Ne pas le réactiver sur une adresse publique.
- Actuator n'expose que `health`, sans détails, sur le port d'administration.
- La chaîne de sécurité exige en plus le rôle `ADMINISTRATEUR` lorsque l'endpoint passe par le contexte applicatif.
- Les sondes d'orchestrateur doivent atteindre le port d'administration depuis un réseau explicitement autorisé.

## 7. Fichiers produit

- Monter `APP_PRODUCT_IMAGES_DIRECTORY` sur un volume non exécutable, non servi directement par le serveur web et accessible en écriture uniquement par l'utilisateur système de l'application.
- Refuser les liens symboliques dans le volume et sauvegarder les métadonnées avec la base.
- Ajouter une analyse antivirus/asynchrone si des utilisateurs non fiables peuvent téléverser des images.
- En multi-instance, remplacer le disque local par un stockage objet privé ; servir les fichiers via l'API ou des URL signées courtes.

## 8. Logs et données personnelles

- Centraliser les logs avec contrôle d'accès, chiffrement et rétention limitée.
- Ne jamais journaliser corps de connexion, en-têtes `Cookie`/`Authorization`/`X-XSRF-TOKEN`, mots de passe ou données bancaires.
- Restreindre l'accès au journal d'activité aux permissions prévues ; sa réponse publique ne contient ni détails libres ni adresse IP.
- Définir une politique de conservation/suppression pour clients, réservations, factures et journaux conformément aux obligations applicables.
- Alertes recommandées : échecs de connexion répétés, erreurs 403/429 anormales, conflits d'idempotence, échec de migration, volume de paiements inhabituel.

## 9. Checklist avant ouverture

- [ ] `SPRING_PROFILES_ACTIVE=prod` visible au démarrage.
- [ ] Aucun secret réel dans Git, l'image ou les logs.
- [ ] `npm audit` retourne 0 vulnérabilité élevée/critique.
- [ ] OWASP Dependency-Check termine avec une base NVD à jour.
- [ ] Tous les tests PostgreSQL/Testcontainers passent.
- [ ] HTTPS seul, HSTS et en-têtes frontend contrôlés avec `curl -I`.
- [ ] Cookie de session contrôlé dans le navigateur (`HttpOnly`, `Secure`, `SameSite=Strict`).
- [ ] Une origine CORS non autorisée est refusée.
- [ ] `/api-docs` et `/swagger-ui.html` sont indisponibles en production.
- [ ] Actuator n'est pas joignable depuis le réseau public.
- [ ] Un changement de rôle/mot de passe invalide une session déjà ouverte.
- [ ] Deux paiements, confirmations, réceptions et réservations simultanés sont rejoués sur PostgreSQL.
- [ ] Sauvegarde et restauration PostgreSQL testées.
- [ ] Volume d'images non exécutable, privé et sauvegardé.
