# Application interne de gestion de restaurant

Ce dépôt accueille une application web réservée au personnel d'un restaurant. Le socle backend Spring Boot est initialisé ; aucune fonctionnalité métier n'est encore implémentée.

## État actuel

À la date du 21 juillet 2026, le dépôt contient un module `backend/` compilable ciblant Java 21, une sécurité restrictive, une route publique de santé, un format d'erreur commun et des tests de démarrage. Il ne contient encore ni module métier, ni entité, ni migration de schéma, ni frontend React.

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

Frontend, lors d'une étape séparée : React, Vite, TypeScript, React Router et Axios.

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
├── frontend/             # créé seulement lors de l'étape frontend
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
| `/actuator/health` | authentification requise | Actuator sans détails, seul endpoint Actuator exposé |
| `/api-docs` | authentification requise | contrat OpenAPI |
| `/swagger-ui.html` | authentification requise | interface OpenAPI |

Tant que le véritable mécanisme d'authentification n'est pas implémenté, aucune identité n'est créée par défaut : les routes protégées répondent donc `401`.

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

Le build exige un JDK 21 ou supérieur et produit du bytecode Java 21. Le profil `test` utilise une base H2 en mémoire pour les tests de démarrage ; Testcontainers PostgreSQL est déjà disponible pour les futurs tests d'intégration.

## Lancer en développement

Une instance PostgreSQL et un mot de passe local sont requis :

```bash
cd backend
export DB_PASSWORD='valeur-locale-non-commitee'
export SPRING_PROFILES_ACTIVE=dev
./mvnw spring-boot:run
```

Les autres paramètres facultatifs sont documentés dans `.env.example`. Le fichier `.env` sert de référence locale mais n'est pas chargé automatiquement par Spring Boot : exportez ses variables dans le processus avant le lancement.

Vérification minimale après démarrage :

```bash
curl -i http://localhost:8080/api/health
curl -i http://localhost:8080/actuator/health
```

## Documentation

- [Architecture](docs/architecture.md)
- [Exigences de sécurité](docs/security-requirements.md)
- [Consignes de contribution pour les agents](AGENTS.md)

## Limites actuelles

- aucun frontend React ;
- aucune fonctionnalité, entité ou migration métier ;
- PostgreSQL est configuré mais aucune instance n'est fournie par le dépôt ;
- aucun mécanisme d'authentification ni compte utilisateur ;
- OpenAPI et Actuator sont protégés mais inutilisables jusqu'à l'ajout de l'authentification ;
- le lancement en profil `dev` nécessite une instance PostgreSQL accessible et correctement configurée.
