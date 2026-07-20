# Architecture de l'application

## État constaté

Le dépôt ne contient actuellement aucun module applicatif, fichier Maven, manifeste npm, source, test, migration ou configuration. L'architecture décrite ici est la cible proposée pour guider les étapes futures ; elle n'est pas encore implémentée.

## Principes

L'application est un outil interne, mais tous les appels doivent être considérés comme non fiables. Le backend demeure l'autorité sur les autorisations, les transitions d'état, les calculs financiers et les invariants métier.

Le projet peut être organisé en deux modules indépendants : `backend/` et, plus tard, `frontend/`. Cette séparation évite de coupler leur cycle de construction tout en conservant un seul dépôt.

## Backend en couches

Le package racine sera choisi lors de la création du socle. Sous celui-ci :

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

## Décisions à différer

Les points suivants ne sont volontairement pas tranchés à l'étape 0 :

- nom du projet, group ID Maven et package racine ;
- version exacte de Spring Boot et de PostgreSQL ;
- authentification par session/cookie ou autre mécanisme ;
- fournisseur d'identité éventuel ;
- structure détaillée par domaine ;
- environnement de déploiement et observabilité ;
- version Node.js supportée lors de l'étape frontend.

Ces décisions doivent être prises au moment où leur étape est demandée, avec leurs conséquences de sécurité et de maintenance.
