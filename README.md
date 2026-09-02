# Application interne de gestion de restaurant

Ce dépôt accueille une application web réservée au personnel d'un restaurant. Le backend métier jusqu'au tableau de bord et le socle frontend d'authentification sont implémentés.

## État actuel

Le dépôt contient un module `backend/` Java 21/Spring Boot et un module `frontend/` React/Vite/TypeScript. Le frontend fournit uniquement la connexion, la session, le layout et les routes de sécurité ; les écrans métier restent à créer.

L'inspection n'a révélé aucun secret applicatif. Les identifiants PostgreSQL sont externalisés et `DB_PASSWORD` est obligatoire hors profil de test. Les fichiers internes de `.git/` ne font pas partie de l'application et ne doivent pas être modifiés manuellement.

## Périmètre prévu

L'application gérera progressivement les utilisateurs et autorisations, employés et clients, tables et réservations, menu, commandes, cuisine, paiements et factures, stocks et recettes, fournisseurs et achats, rapports et journaux d'activité.

Rôles prévus :

- `ADMINISTRATEUR`
- `GERANT`
- `SERVEUR`
- `CAISSIER`
- `CUISINE`
- `RESPONSABLE_STOCK`

## Technologies cibles

Backend : Java 21, Spring Boot 3.5.16, Maven 3.9.9 via Wrapper, Spring Web, Spring Security, Spring Data JPA, Jakarta Bean Validation, PostgreSQL, Flyway, springdoc OpenAPI 2.8.17, Actuator, JUnit 5, Mockito et Testcontainers.

Frontend : React 19, Vite 8, TypeScript strict, Fetch natif, API History, Vitest et Testing Library.

Les dépendances couvertes par Spring Boot utilisent sa gestion de versions. Spring Boot 3.5.16 est la dernière version 3.x communautaire publiée, mais cette ligne arrive en fin de support OSS et devra faire l'objet d'une décision de mise à niveau lors d'une étape dédiée.

## Environnement constaté

| Outil | Version constatée | Situation |
|---|---:|---|
| Java | OpenJDK 21.0.11 | Conforme à Java 21 requis |
| Maven | 3.9.9 | Disponible |
| Node.js | 24.10.0 | Disponible, version cible à fixer à l'étape frontend |
| npm | 11.6.0 | Disponible |
| PostgreSQL CLI (`psql`) | absent | Facultatif si PostgreSQL est utilisé via Docker |
| Docker | 29.0.1 | Disponible |
| Docker Compose | 2.40.3 | Disponible |

Ces valeurs décrivent la machine inspectée et ne constituent pas encore un contrat de version du projet.

## Arborescence actuelle

```text
restaurant project/
├── .env.example
├── .gitignore
├── AGENTS.md
├── README.md
├── backend/
│   ├── .mvn/wrapper/maven-wrapper.properties
│   ├── mvnw
│   ├── mvnw.cmd
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/restaurant/management/
│       │   └── resources/
│       └── test/
│           ├── java/com/restaurant/management/
│           └── resources/
└── docs/
    ├── architecture.md
    └── security-requirements.md
```

## Arborescence cible proposée

Cette structure complète le backend existant avec les éléments futurs, sans les créer maintenant :

```text
restaurant project/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/<package-racine>/
│       │   │   ├── config/
│       │   │   ├── controller/
│       │   │   ├── dto/
│       │   │   ├── entity/
│       │   │   ├── enums/
│       │   │   ├── exception/
│       │   │   ├── mapper/
│       │   │   ├── repository/
│       │   │   ├── security/
│       │   │   ├── service/
│       │   │   └── validation/
│       │   └── resources/
│       │       ├── db/migration/
│       │       └── application.yml
│       └── test/
│           └── java/<package-racine>/
├── frontend/
│   ├── package.json
│   └── src/
├── docs/
├── .env.example
├── .gitignore
├── AGENTS.md
└── README.md
```

Le package racine du backend est `com.restaurant.management`. L'éventuelle infrastructure Docker et la structure métier détaillée restent à décider dans leurs étapes respectives.

## Routes actuellement disponibles

| Route | Accès | Contenu |
|---|---|---|
| `GET /api/health` | public | `{"status":"UP"}` uniquement |
| `GET /api/auth/csrf` | public | jeton CSRF pour les requêtes modifiant l'état |
| `POST /api/auth/login` | public + CSRF | ouvre une session authentifiée |
| `GET /api/auth/me` | authentifié | utilisateur, employé, rôles et permissions |
| `POST /api/auth/logout` | authentifié + CSRF | invalide la session |
| `/actuator/health` | authentification requise | Actuator sans détails, seul endpoint Actuator exposé |
| `/api-docs` | authentification requise | contrat OpenAPI |
| `/swagger-ui.html` | authentification requise | interface OpenAPI |

Aucun compte n'est créé par migration. Un utilisateur doit être créé avec un hash BCrypt par un mécanisme d'administration futur ou une opération locale contrôlée avant de pouvoir ouvrir une session.

## Vérifier l'environnement

Depuis la racine du dépôt :

```bash
git status
java -version
mvn -version
node --version
npm --version
psql --version
docker --version
docker compose version
```

Avant de créer le backend, installer ou sélectionner un JDK 21 puis confirmer que `java -version` et la ligne `Java version` de `mvn -version` indiquent tous deux Java 21.

## Compiler et tester le backend

```bash
cd backend
./mvnw clean verify
```

Le build exige un JDK 21 ou supérieur et produit du bytecode Java 21. Le profil `test` utilise H2 pour les tests de démarrage. Les tests de persistance utilisent un PostgreSQL 17 éphémère avec Testcontainers et sont ignorés lorsque Docker n'est pas disponible.

Exécuter uniquement les tests d'intégration PostgreSQL :

```bash
cd backend
./mvnw -Dtest=SecurityPersistenceIntegrationTest test
./mvnw -Dtest=AuthenticationIntegrationTest test
```

## Lancer en développement

Une instance PostgreSQL et un mot de passe local sont requis :

```bash
cd backend
export DB_PASSWORD='valeur-locale-non-commitee'
export SPRING_PROFILES_ACTIVE=dev
./mvnw spring-boot:run
```

Lorsque PostgreSQL est fourni par le conteneur local `restaurant-postgres`, le
lanceur suivant récupère son utilisateur, sa base et son mot de passe sans les
écrire dans le dépôt ni les afficher dans le terminal :

```bash
cd backend
./run-dev.sh
```

Le nom du conteneur peut être adapté avec `POSTGRES_CONTAINER_NAME`. Les
variables `DB_USERNAME`, `DB_NAME` et `DB_PASSWORD` déjà définies restent
prioritaires.

Les autres paramètres facultatifs sont documentés dans `.env.example`. Le fichier `.env` sert de référence locale mais n'est pas chargé automatiquement par Spring Boot : exportez ses variables dans le processus avant le lancement.

Vérification minimale après démarrage :

```bash
curl -i http://localhost:8080/api/health
curl -i http://localhost:8080/actuator/health
```

Dans un second terminal :

```bash
cd frontend
npm install
npm run dev
```

Le frontend est alors disponible sur `http://localhost:5173` et relaie les
appels `/api` vers le backend local.

## Documentation

- [Architecture](docs/architecture.md)
- [Exigences de sécurité](docs/security-requirements.md)
- [Schéma sécurité et employés](docs/security-employee-schema.md)
- [Consignes de contribution pour les agents](AGENTS.md)
- [Fondation frontend](docs/frontend-foundation.md)

## Limites actuelles

- aucun écran métier frontend au-delà de la session et des pages de sécurité ;
- PostgreSQL est configuré mais aucune instance n'est fournie par le dépôt ;
- aucun compte utilisateur initial ni endpoint de création de compte ;
- OpenAPI et Actuator nécessitent une session authentifiée ;
- le lancement en profil `dev` nécessite une instance PostgreSQL accessible et correctement configurée.
