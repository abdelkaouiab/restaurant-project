# Docker et préparation du déploiement

Ce document couvre uniquement l'étape 15. La pile contient PostgreSQL, le backend Spring Boot, le frontend statique et un reverse proxy Nginx. Le reverse proxy est la seule entrée publiée. PostgreSQL, le backend et le frontend restent sur des réseaux Docker privés.

## 1. Architecture et garanties

| Service | Image | Utilisateur | Exposition |
| --- | --- | --- | --- |
| PostgreSQL | `postgres:17.11-alpine3.24` | utilisateur `postgres` de l'image | réseau interne `database`, aucun port hôte |
| Backend | construction Maven puis `eclipse-temurin:21-jre-alpine-3.24` | UID/GID `10001` | ports 8080/8081 non publiés |
| Frontend | construction Node puis `nginx:1.30.4-alpine3.24` | UID/GID `101` | port 8080 non publié |
| Reverse proxy | configuration immuable sur `nginx:1.30.4-alpine3.24` | UID/GID non-root configurables | seuls ports HTTP/HTTPS publiés |

Les images applicatives utilisent une racine en lecture seule, suppriment toutes les capabilities Linux, interdisent l'élévation de privilèges et limitent mémoire, CPU et processus. Les journaux Docker tournent à 10 Mio avec cinq fichiers et le format Nginx omet les paramètres de requête, cookies et en-têtes d'authentification.

Les tags de versions sont volontairement explicites. Avant chaque livraison, reconstruire avec les derniers correctifs compatibles, analyser les images et, dans un registre de production, verrouiller les images validées par digest.

## 2. Prérequis et secrets

- Docker Engine ou Docker Desktop avec Compose v2 ;
- OpenSSL pour le certificat local ;
- un nom DNS et un certificat de confiance en production ;
- un mot de passe PostgreSQL unique fourni à l'exécution.

Copier l'exemple puis remplacer toutes les valeurs fictives :

```bash
cp .env.example .env
chmod 600 .env
```

Le fichier `.env` est ignoré par Git. En production, préférer le coffre de secrets de la plateforme et injecter les variables au processus Compose plutôt que conserver des secrets sur disque. Ne jamais passer un secret en argument de construction Docker.

Variables essentielles :

```text
DB_PASSWORD=<secret long et unique>
APP_PUBLIC_ORIGIN=https://restaurant.example.com
APP_BIND_ADDRESS=0.0.0.0
APP_HTTP_PORT=80
APP_HTTPS_PORT=443
TLS_CERTIFICATE_PATH=/chemin/absolu/fullchain.pem
TLS_PRIVATE_KEY_PATH=/chemin/absolu/privkey.pem
PROXY_UID=<uid non-root autorisé à lire les certificats>
PROXY_GID=<gid non-root autorisé à lire les certificats>
```

Le certificat et surtout sa clé doivent rester hors du dépôt. Sur Linux, créer un compte système dédié au proxy ou donner une ACL de lecture à son UID, sans rendre la clé lisible par tous. Exemple avec un compte dont l'UID/GID vaut `101` :

```bash
sudo chown root:101 /etc/restaurant/tls/privkey.pem
sudo chmod 640 /etc/restaurant/tls/privkey.pem
sudo chmod 644 /etc/restaurant/tls/fullchain.pem
```

## 3. Certificat de développement local

Pour un test local uniquement, générer un certificat auto-signé dans un répertoire privé :

```bash
mkdir -p "$HOME/.restaurant-local-tls"
chmod 700 "$HOME/.restaurant-local-tls"
openssl req -x509 -newkey rsa:3072 -sha256 -days 30 -nodes \
  -subj '/CN=localhost' \
  -addext 'subjectAltName=DNS:localhost,IP:127.0.0.1' \
  -keyout "$HOME/.restaurant-local-tls/privkey.pem" \
  -out "$HOME/.restaurant-local-tls/fullchain.pem"
chmod 600 "$HOME/.restaurant-local-tls/privkey.pem"
```

Renseigner leurs chemins absolus dans `.env`. Pour un poste local, `PROXY_UID=$(id -u)` et `PROXY_GID=$(id -g)` permettent au proxy non-root de lire ces fichiers. Le navigateur avertira que le certificat auto-signé n'est pas fiable ; ne jamais l'utiliser en production.

## 4. Compilation et lancement

Valider la configuration sans afficher les valeurs résolues :

```bash
docker compose config --quiet
```

Construire et démarrer, en attendant que toutes les sondes soient saines :

```bash
docker compose build
docker compose up -d --wait --wait-timeout 300
docker compose ps
```

Ouvrir `https://localhost:8443` avec les valeurs locales de l'exemple. Le port HTTP 8080 ne sert qu'à rediriger vers HTTPS. En production, publier 80 et 443 et filtrer ces ports au pare-feu ; aucun autre port de la pile ne doit être ouvert.

Commandes de diagnostic sans divulguer l'environnement des conteneurs :

```bash
docker compose ps
docker compose logs --tail=100 backend
docker compose logs --tail=100 reverse-proxy
curl -kfsS https://localhost:8443/api/health
curl -kI https://localhost:8443/
```

Arrêter les conteneurs sans supprimer les données :

```bash
docker compose down
```

Ne lancer `docker compose down --volumes` que pour détruire explicitement une installation de test : cette commande supprime la base et les images produit persistantes.

## 5. Migrations Flyway

Flyway est la source de vérité du schéma. Au démarrage du backend, il applique automatiquement les migrations versionnées avant que la sonde de santé ne passe. Le profil `prod` valide le schéma et interdit `flyway clean`.

Procédure de livraison :

1. ajouter une migration immuable `V<n>__description.sql` sous `backend/src/main/resources/db/migration` ;
2. exécuter les tests backend puis créer une sauvegarde ;
3. reconstruire l'image backend ;
4. démarrer PostgreSQL, puis le backend ;
5. vérifier les logs Flyway et la sonde avant d'envoyer du trafic au proxy.

```bash
cd backend
./mvnw clean test
cd ..
docker compose build backend
docker compose up -d postgres backend --wait --wait-timeout 300
docker compose logs --tail=200 backend
docker compose up -d frontend reverse-proxy --wait --wait-timeout 120
```

Ne jamais modifier une migration déjà appliquée. Pour une migration nécessitant une longue indisponibilité, préparer une procédure dédiée et testée sur une restauration de sauvegarde.

## 6. Sauvegarde

Les volumes `postgres-data` et `product-images` sont persistants. La base et les images produit doivent être sauvegardées de manière cohérente, chiffrées hors de l'hôte et soumises à une politique de rétention.

Sauvegarde logique PostgreSQL :

```bash
umask 077
mkdir -p backups
docker compose exec -T postgres sh -c \
  'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --format=custom --no-owner --no-acl' \
  > "backups/restaurant-$(date +%Y%m%d-%H%M%S).dump"
```

Sauvegarder aussi le volume d'images avec l'outil de sauvegarde de la plateforme. Ne pas copier un volume PostgreSQL en cours d'écriture comme simple dossier ; utiliser `pg_dump` ou une solution PostgreSQL cohérente. Contrôler périodiquement que le fichier n'est pas vide et tester réellement sa restauration.

## 7. Restauration

La restauration suivante détruit le contenu actuel de la base ciblée. Elle doit être exécutée seulement après validation du nom de projet, du fichier de sauvegarde et d'une nouvelle sauvegarde de sécurité.

```bash
docker compose stop reverse-proxy backend
docker compose exec -T postgres sh -c \
  'PGPASSWORD="$POSTGRES_PASSWORD" dropdb --force --username="$POSTGRES_USER" "$POSTGRES_DB" && PGPASSWORD="$POSTGRES_PASSWORD" createdb --username="$POSTGRES_USER" "$POSTGRES_DB"'
docker compose exec -T postgres sh -c \
  'PGPASSWORD="$POSTGRES_PASSWORD" pg_restore --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --no-owner --no-acl' \
  < backups/restaurant-AAAAMMJJ-HHMMSS.dump
docker compose up -d backend frontend reverse-proxy --wait --wait-timeout 300
```

Restaurer ensuite le volume d'images correspondant au même point de sauvegarde, puis vérifier les migrations, quelques commandes, factures et images avant la remise en service.

## 8. Rotation des secrets et certificats

### Mot de passe PostgreSQL

1. ouvrir une fenêtre de maintenance et arrêter le proxy ;
2. changer le mot de passe de façon interactive, afin qu'il ne figure ni dans l'historique shell ni dans les logs ;
3. mettre à jour le coffre de secrets ou `.env` ;
4. recréer PostgreSQL et le backend pour synchroniser leur environnement ;
5. vérifier la connexion puis rouvrir le proxy.

```bash
docker compose stop reverse-proxy
docker compose exec postgres sh -c \
  'PGPASSWORD="$POSTGRES_PASSWORD" psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --command="\\password $POSTGRES_USER"'
# Mettre maintenant DB_PASSWORD à jour dans la source de secrets.
docker compose up -d --force-recreate postgres backend --wait --wait-timeout 300
docker compose up -d reverse-proxy --wait --wait-timeout 120
```

### Certificat TLS

Installer les nouveaux fichiers hors Git, contrôler la chaîne et les permissions, mettre à jour les chemins si nécessaire, puis recréer le proxy :

```bash
docker compose run --rm --no-deps --entrypoint nginx reverse-proxy -t
docker compose up -d --force-recreate reverse-proxy --wait --wait-timeout 120
```

Le renouvellement automatique et la distribution du certificat relèvent de l'hébergeur ou d'un client ACME externe ; aucune clé privée n'est embarquée dans l'image.

## 9. Test automatisé local

Le script de fumée crée un projet Compose isolé, un certificat et un mot de passe éphémères, construit les deux applications, attend les quatre healthchecks, vérifie HTTPS, l'API et HSTS, puis supprime uniquement ses volumes de test :

```bash
./scripts/docker-smoke-test.sh
```

Il ne déploie rien et n'utilise aucun service externe autre que les registres nécessaires au téléchargement des images et dépendances.

## 10. Checklist de production

- [ ] Toutes les images ont été reconstruites, analysées et verrouillées par digest dans le manifeste de livraison.
- [ ] `SPRING_PROFILES_ACTIVE=prod` et Flyway démarre sans erreur ; `flyway clean` reste interdit.
- [ ] Aucun secret, certificat privé, `.env`, sauvegarde ou donnée produit n'est présent dans Git ou une couche d'image.
- [ ] Le mot de passe PostgreSQL est unique ; le rôle applicatif n'est ni superutilisateur ni créateur de rôle/base.
- [ ] PostgreSQL, 8080 applicatif et 8081 management ne sont pas publiés ; seuls 80/443 sont autorisés au pare-feu.
- [ ] Le certificat est valide, renouvelé automatiquement ; TLS 1.0/1.1 sont refusés et HTTP redirige vers HTTPS.
- [ ] HSTS, CSP, `nosniff`, anti-framing, Referrer-Policy et Permissions-Policy sont présents.
- [ ] Les cookies de session sont `Secure`, `HttpOnly`, `SameSite=Strict` et CORS contient uniquement l'origine HTTPS réelle.
- [ ] Les limites de corps, débit, connexions, délais, CPU, mémoire, processus et rotation des logs sont adaptées à la charge mesurée.
- [ ] Le volume d'images est privé, non exécutable si la plateforme le permet, persistant et sauvegardé.
- [ ] Les sauvegardes sont chiffrées, externalisées, surveillées et une restauration récente a réussi.
- [ ] Les logs centralisés n'enregistrent ni cookies, jetons, mots de passe, paramètres de requête ni données bancaires.
- [ ] Swagger reste désactivé et le port Actuator n'est accessible que depuis le conteneur backend.
- [ ] Les sondes et une connexion fonctionnelle ont été validées après redémarrage complet.

## 11. Limites restantes

- Compose fournit une exécution sur un seul hôte, sans haute disponibilité ni ordonnanceur de reprise inter-hôtes.
- Les sessions et le limiteur de connexion backend restent locaux à une instance ; plusieurs backends exigent un stockage partagé avant mise à l'échelle.
- Le renouvellement ACME, le chiffrement/externalisation planifiés des sauvegardes et la supervision centralisée dépendent de l'infrastructure cible.
- Le réseau `database` est privé à Docker mais n'ajoute pas TLS entre backend et PostgreSQL ; activer PostgreSQL TLS si le réseau sort d'un hôte unique ou n'est pas totalement maîtrisé.
- Les limites Nginx sont des valeurs initiales prudentes à ajuster après tests de charge, sans supprimer les protections.
