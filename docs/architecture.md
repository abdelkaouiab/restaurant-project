# Architecture de l'application

## État constaté

Le dépôt contient le socle `backend/` Spring Boot sous le package racine `com.restaurant.management`. L'étape 2 ajoute le modèle persistant des utilisateurs, rôles, permissions, employés et journaux d'activité. L'étape 3 fournit une authentification REST par session sécurisée, sans contrôleur CRUD. Le frontend n'est pas créé.

## Principes

L'application est un outil interne, mais tous les appels doivent être considérés comme non fiables. Le backend demeure l'autorité sur les autorisations, les transitions d'état, les calculs financiers et les invariants métier.

Le projet peut être organisé en deux modules indépendants : `backend/` et, plus tard, `frontend/`. Cette séparation évite de coupler leur cycle de construction tout en conservant un seul dépôt.

## Backend en couches

Sous le package racine `com.restaurant.management` :

| Couche | Responsabilité | Ne doit pas faire |
|---|---|---|
| `controller` | HTTP, validation initiale, statut et DTO | règles métier, accès JPA direct |
| `dto` | contrats d'entrée/sortie minimaux | exposer des relations JPA |
| `service` | cas d'usage, règles métier, transactions | dépendre du transport HTTP |
| `repository` | accès persistant et requêtes ciblées | décider des autorisations métier |
| `entity` | modèle persistant et contraintes locales | servir de contrat API |
| `mapper` | conversion explicite DTO/domaine | accepter aveuglément tous les champs entrants |
| `security` | authentification, autorisation, utilisateur courant | faire confiance au seul frontend |
| `validation` | contraintes réutilisables et validation spécialisée | remplacer les contrôles métier transactionnels |
| `exception` | exceptions typées et gestion globale | divulguer les traces ou détails internes |
| `config` | configuration technique typée | contenir des secrets codés en dur |
| `enums` | états et valeurs fermées du domaine | reproduire des chaînes libres incohérentes |

Flux attendu :

```text
Requête HTTP -> Security -> Controller/DTO -> Service/Transaction
                                         -> Repository -> PostgreSQL
Réponse HTTP <- gestion d'erreurs <- DTO/Mapper <- Service
```

## Frontend futur

Le frontend React sera créé dans une étape distincte. Il pourra gérer la navigation et l'expérience utilisateur, mais ses contrôles ne remplaceront jamais la validation ou l'autorisation serveur. Axios centralisera la configuration HTTP sans incorporer de secret dans le bundle.

## Données et migrations

- PostgreSQL est la base cible.
- Flyway versionnera chaque évolution de schéma.
- Une migration appliquée ne sera pas réécrite ; une correction utilisera une nouvelle migration.
- Les montants utiliseront `BigDecimal` en Java et `NUMERIC` en PostgreSQL avec précision explicite.
- Les dates/heures seront stockées avec une stratégie de fuseau documentée, de préférence en UTC pour les instants.
- Les contraintes uniques, clés étrangères et contrôles pertinents renforceront les règles au niveau de la base.
- `@Version` protégera les agrégats sujets aux mises à jour concurrentes.
- Les opérations à effet unique utiliseront une contrainte ou clé d'idempotence en plus du contrôle applicatif.

## API

- Contrats DTO explicites, validation stricte et prévention du mass assignment.
- Pagination obligatoire pour les collections susceptibles de croître.
- Champs de tri limités par liste blanche.
- Format d'erreur commun avec code stable, message public, horodatage et identifiant de corrélation ; aucune stack trace.
- OpenAPI documentera uniquement les contrats réellement disponibles.
- Une stratégie de versionnement d'API sera décidée avant la première API stable.

## Décisions actées à l'étape 1

- module backend sous `backend/` ;
- coordonnées Maven `com.restaurant:restaurant-backend` ;
- package racine `com.restaurant.management` ;
- cible Java 21 et Maven Wrapper 3.9.9 ;
- Spring Boot 3.5.16 et springdoc 2.8.17 ;
- refus HTTP par défaut, avec seule la route `GET /api/health` publique ;
- Actuator limité à `health`, détails masqués et route protégée ;
- H2 réservé aux tests de démarrage, PostgreSQL restant la base applicative cible.

## Décisions actées à l'étape 2

- identifiants techniques `BIGINT` générés par PostgreSQL ;
- relation obligatoire `Utilisateur -> Employe`, avec au maximum un compte par employé ;
- relations plusieurs-à-plusieurs entre utilisateurs/rôles et rôles/permissions ;
- e-mails normalisés en minuscules et uniques sans tenir compte de la casse ;
- désactivation logique, timestamps UTC (`Instant`/`TIMESTAMPTZ`) et verrouillage optimiste sur les agrégats mutables ;
- journal d'activité append-only avec identifiant de corrélation et acteur nullable ;
- référentiels initiaux de rôles et permissions, sans compte préconfiguré ;
- validation d'intégration avec PostgreSQL 17 éphémère via Testcontainers.

Le schéma détaillé est présenté dans [security-employee-schema.md](security-employee-schema.md).

## Décisions actées à l'étape 11

- agrégat `Achat` composé de lignes portant le prix d'achat historique ;
- calcul exclusif des sous-totaux et du total côté serveur ;
- réceptions partielles persistées séparément avec une clé UUID d'idempotence ;
- un mouvement d'entrée de stock par ingrédient et par réception, dans la même transaction ;
- verrou pessimiste de l'achat avant contrôle des reliquats, puis verrou ordonné des ingrédients ;
- aucune modification de ligne après passage du brouillon à l'état commandé ;
- annulation limitée aux états `BROUILLON` et `COMMANDE` ;
- fournisseurs et achats consultables par recherche paginée et tri en liste blanche.

Le schéma, les transitions et les routes sont détaillés dans
[suppliers-and-purchases.md](suppliers-and-purchases.md).

## Décisions actées à l'étape 12

- séparation des indicateurs opérationnels et financiers ;
- données financières réservées simultanément par rôle et permission ;
- périodes inclusives en dates civiles, converties en bornes UTC semi-ouvertes ;
- durée maximale de 366 jours et période par défaut de trente jours ;
- agrégations SQL directes et index dédiés pour éviter les requêtes N+1 ;
- consultation du journal uniquement par DTO paginé, sans détails libres ni adresse IP ;
- aucune API de modification ou suppression du journal.

Les conventions de calcul et les routes sont détaillées dans
[dashboard-and-audit.md](dashboard-and-audit.md).

## Décisions actées à l'étape 3

- authentification par session serveur, sans jeton durable côté navigateur ;
- mot de passe vérifié avec BCrypt, coût 12 configurable ;
- renouvellement de l'identifiant de session à la connexion et invalidation à la déconnexion ;
- cookie de session `HttpOnly`, `SameSite=Strict`, `Secure` par défaut et expiration après 30 minutes d'inactivité ;
- protection CSRF par cookie dédié et en-tête `X-XSRF-TOKEN` ;
- CORS avec origines explicites et credentials uniquement pour ces origines ;
- limitation mémoire par couple adresse IP/e-mail normalisé, cinq échecs sur quinze minutes ;
- même erreur publique pour compte inconnu, mot de passe incorrect et compte inactif ;
- audit persistant des connexions réussies/échouées et déconnexions, sans e-mail ni secret dans les événements d'échec.

Flux d'authentification :

```text
GET  /api/auth/csrf   -> cookie XSRF-TOKEN + jeton dans la réponse
POST /api/auth/login  -> BCrypt -> rotation de session -> cookie RESTAURANT_SESSION
GET  /api/auth/me     -> DTO utilisateur/employé/rôles/permissions
POST /api/auth/logout -> CSRF -> audit -> invalidation de session
```

## Décisions actées à l'étape 5

- référentiels `Employe`, `Client`, `TableRestaurant`, `Categorie` et `Produit` exposés uniquement par DTO ;
- aucune suppression physique : les cinq ressources sont activées ou désactivées ;
- prix stockés en `NUMERIC(12,2)` et manipulés avec `BigDecimal` ;
- catégorie obligatoire et active lors de la création ou modification d'un produit ;
- désactivation d'une catégorie refusée tant qu'elle contient un produit actif ;
- désactivation d'un employé refusée si elle désactiverait le dernier administrateur actif ;
- listes paginées, recherche bornée et champs de tri propres à chaque ressource ;
- autorisations fondées sur des permissions attribuées aux rôles existants par Flyway ;
- journalisation persistante des changements de prix et des désactivations ;
- les images de produit sont stockées hors base dans un répertoire configurable ; le serveur génère leur nom, vérifie leur signature et limite les formats à PNG, JPEG et WebP ainsi que la taille à 5 Mo par défaut ;
- la disponibilité métier d'un produit est distincte de son état `actif` : elle peut suspendre sa prise de commande sans supprimer ni désactiver le produit historique.

Le schéma et les routes sont détaillés dans
[restaurant-reference-data.md](restaurant-reference-data.md).

## Décisions actées à l'étape 6

- une commande appartient à son serveur créateur et possède un numéro métier unique généré côté serveur ;
- les gérants et administrateurs peuvent superviser toutes les commandes, tandis qu'un serveur est limité aux siennes ;
- transitions limitées à `BROUILLON -> CONFIRMEE -> ANNULEE` et `BROUILLON -> ANNULEE` ;
- table obligatoire et client optionnel, tous deux actifs lors de leur association ;
- lignes modifiables uniquement à l'état brouillon ;
- prix unitaire copié depuis le produit lors de l'ajout, puis conservé comme instantané historique ;
- sous-totaux et total recalculés exclusivement côté serveur avec une échelle de deux décimales ;
- confirmation refusée sans ligne ou si la table, le client, un produit ou sa catégorie n'est plus actif ;
- verrouillage optimiste sur la commande et les lignes, avec conflit HTTP `409` ;
- corps JSON inconnus, prix et totaux fournis par le client rejetés.

Le schéma et les routes sont détaillés dans [orders.md](orders.md).

## Décisions à différer

Les points suivants ne sont volontairement pas tranchés à l'étape 0 :

- version majeure exacte de PostgreSQL ;
- authentification par session/cookie ou autre mécanisme ;
- fournisseur d'identité éventuel ;
- structure détaillée par domaine ;
- environnement de déploiement et observabilité ;
- version Node.js supportée lors de l'étape frontend.

Ces décisions doivent être prises au moment où leur étape est demandée, avec leurs conséquences de sécurité et de maintenance.
# Flux cuisine (étape 7)

Le flux cuisine est isolé sous `/api/cuisine/commandes` et utilise une projection
dédiée sans données financières ni personnelles. Les transitions sont portées
par l’agrégat `Commande`, protégées par verrouillage optimiste et accompagnées
d’un journal d’activité. Une notification interne persistante est créée lorsque
la commande devient prête. Voir `docs/kitchen-flow.md`.

## Décisions actées à l’étape 8

- seules les commandes `PRETE` sont payables ;
- paiements fractionnés autorisés, sans surpaiement ni rendu de monnaie ;
- restant dû recalculé depuis les paiements validés ;
- idempotence obligatoire par UUID et verrou pessimiste par commande ;
- passage à `PAYEE` et émission d’une facture unique seulement au paiement complet ;
- facture persistée comme instantané immuable ;
- aucune donnée bancaire sensible stockée ou journalisée.

Le schéma et les routes sont détaillés dans
[payments-and-invoices.md](payments-and-invoices.md).

## Décisions actées à l'étape 9

- durée fixe de deux heures, calculée par le serveur ;
- intervalles semi-ouverts `[début, fin)` ;
- conflit contrôlé pour les réservations en attente, confirmées ou arrivées ;
- verrou pessimiste sur la table avant la vérification et l'insertion ;
- cycle d'état explicite, avec libération en cas de terminaison, annulation ou absence ;
- commande unique créée uniquement après l'arrivée, avec table et client hérités de la réservation.

Le détail est présenté dans [reservations.md](reservations.md).

## Décisions actées à l'étape 10

- déduction automatique exclusivement lors de la confirmation d'une commande ;
- recette produit-ingrédient avec quantités à trois décimales ;
- tout changement de solde matérialisé par un mouvement immuable ;
- idempotence persistante par clé manuelle ou couple commande-ingrédient ;
- verrou pessimiste sur l'ingrédient et verrouillage optimiste des agrégats ;
- stock négatif réservé à une permission exceptionnelle d'administrateur ;
- alertes lorsque le disponible atteint le seuil configuré.

Voir [stock-management.md](stock-management.md).

## Décisions actées à l'étape 13

- module `frontend/` indépendant en React, Vite et TypeScript strict ;
- séparation `api`, `auth`, `components`, `features`, `layouts`, `pages`, `routes`, `types` et `utils` ;
- appels Fetch avec `credentials: include` et jeton CSRF uniquement en mémoire ;
- restauration de session via `/api/auth/me` au démarrage ;
- routes et layout protégés, avec masquage visuel par permission ;
- aucun écran métier, aucun secret dans les stockages navigateur et aucun HTML injecté ;
- vérifications d'autorisation serveur conservées comme autorité réelle.

Les conventions de sécurité et de lancement sont détaillées dans
[frontend-foundation.md](frontend-foundation.md).
