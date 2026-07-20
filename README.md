# Application interne de gestion de restaurant

Ce dépôt accueillera une application web réservée au personnel d'un restaurant. L'étape 0 couvre uniquement l'analyse du dépôt et la préparation de sa documentation. Aucune fonctionnalité métier n'est encore implémentée.

## État actuel

À la date du 20 juillet 2026, le dépôt ne contient aucun code applicatif, aucun fichier de construction et aucun historique de migration. Il n'existe donc pas encore de backend Spring Boot, de frontend React, de schéma PostgreSQL, de test automatisé ni de dépendance déclarée.

L'inspection n'a révélé aucun secret applicatif dans les fichiers suivis. Le dépôt ne possède actuellement aucun commit ni dépôt distant configuré. Les fichiers internes de `.git/` ne font pas partie de l'application et ne doivent pas être modifiés manuellement.

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

Backend : Java 21, Spring Boot 3.x, Maven, Spring Web, Spring Security, Spring Data JPA, Jakarta Bean Validation, PostgreSQL, Flyway, OpenAPI, JUnit 5, Mockito et Testcontainers.

Frontend, lors d'une étape séparée : React, Vite, TypeScript, React Router et Axios.

Les versions précises de Spring Boot, PostgreSQL, Node.js et des bibliothèques seront fixées au moment de leur introduction, après vérification de leur compatibilité et de leur support. Aucune version non constatée n'est inventée à cette étape.

## Environnement constaté

| Outil | Version constatée | Situation |
|---|---:|---|
| Java | OpenJDK 17.0.17 | Non conforme : Java 21 requis |
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
└── .git/                 # métadonnées Git uniquement
```

Après l'étape 0 :

```text
restaurant project/
├── .env.example
├── .gitignore
├── AGENTS.md
├── README.md
└── docs/
    ├── architecture.md
    └── security-requirements.md
```

## Arborescence cible proposée

Cette structure est une proposition pour les prochaines étapes, pas du code déjà créé :

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

Le nom du package racine, les modules exacts et l'éventuelle infrastructure Docker devront être décidés à l'étape de création du socle.

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

Les commandes suivantes deviendront applicables uniquement après la création des modules correspondants :

```bash
cd backend && ./mvnw verify
cd frontend && npm install && npm run build
```

## Documentation

- [Architecture](docs/architecture.md)
- [Exigences de sécurité](docs/security-requirements.md)
- [Consignes de contribution pour les agents](AGENTS.md)

## Limites actuelles

- aucun socle Spring Boot ou React ;
- aucune connexion PostgreSQL ni migration Flyway ;
- aucune authentification ou autorisation ;
- aucun test exécutable ;
- aucune commande de compilation/lancement applicatif tant que les modules ne sont pas créés ;
- Java 21 n'est pas actif dans l'environnement inspecté.
