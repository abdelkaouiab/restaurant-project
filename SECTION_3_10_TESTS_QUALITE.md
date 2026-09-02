# Section 3.10 — Stratégie de tests et qualité

> Ce document rassemble uniquement des informations vérifiées dans le code du projet, dans les rapports de tests locaux ou dans les résultats de validation fournis. Il ne suppose pas que l'application a été déployée en production.

## Synthèse du diagnostic

Le backend dispose de 107 tests qui ont tous été exécutés localement avec succès :

```text
Tests run: 107, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Les rapports Surefire locaux confirment également :

```text
tests=107 failures=0 errors=0 skipped=0
```

Les tests d'intégration backend utilisent Testcontainers avec PostgreSQL. Les migrations Flyway V1 à V12 sont appliquées pendant ces tests et Hibernate vérifie ensuite la conformité du schéma avec les entités JPA.

Le frontend est testé avec Vitest. La suite frontend ne peut pas encore être déclarée entièrement réussie : un test du module de gestion des stocks échoue en raison d'une différence entre le texte attendu par le test et celui réellement généré par le composant React.

## 1. Analyse des tests backend

### 1.1 Outils réellement utilisés

Le backend utilise les outils suivants :

- JUnit 5/JUnit Jupiter pour déclarer les tests ;
- Maven Surefire pour leur exécution par `./mvnw test` ;
- AssertJ pour les assertions Java ;
- Spring Boot Test ;
- Spring Test et MockMvc pour les tests HTTP ;
- Spring Security Test pour simuler les utilisateurs et la protection CSRF ;
- Mockito, directement dans plusieurs tests ;
- Testcontainers avec PostgreSQL 17 Alpine ;
- H2 pour les tests légers qui ne nécessitent pas les particularités de PostgreSQL ;
- Jakarta Validation pour les tests de contraintes.

Preuves principales :

- `backend/pom.xml` ;
- `backend/src/test/java/` ;
- `backend/src/test/resources/application-test.yml`.

### 1.2 Classification des tests backend

| Catégorie | Nombre de tests | Exemples de fichiers |
|---|---:|---|
| Tests isolés ou unitaires | 10 | configuration production, limitation des connexions, filtre de session, stockage d'images, validation |
| Slice MVC avec `@WebMvcTest` | 2 | `GlobalExceptionHandlerTest` |
| Slice JPA avec `@DataJpaTest` | 1 | `JournalActiviteRepositoryTest` |
| `@SpringBootTest` avec H2 | 6 | démarrage du contexte et contrôleur de santé |
| Intégrations PostgreSQL/Testcontainers | 88 | onze classes d'intégration |
| **Total** | **107** | **0 échec, 0 erreur, 0 test ignoré** |

### 1.3 Tests unitaires ou isolés

Les tests suivants n'initialisent pas l'ensemble de l'application avec une base PostgreSQL :

- `ProductionSecurityConfigurationTest` lit et vérifie la configuration de production ;
- `LoginAttemptServiceTest` vérifie la fenêtre temporelle, le blocage et la remise à zéro des tentatives de connexion ;
- `SessionSecurityValidationFilterTest` instancie directement le filtre de validation des sessions avec des requêtes et sessions simulées ;
- `ProduitImageStorageServiceTest` vérifie le stockage et la validation d'images dans un répertoire temporaire ;
- `SecurityInputValidationTest` utilise directement Jakarta Validation sur les DTO et les paramètres des contrôleurs.

### 1.4 Tests utilisant Spring Boot Test

`@SpringBootTest` est utilisé par :

- `RestaurantManagementApplicationTests` ;
- `HealthControllerTest` ;
- `AchatIntegrationTest` ;
- `CommandeIntegrationTest` ;
- `CuisineCommandeIntegrationTest` ;
- `PaiementIntegrationTest` ;
- `ReferentielsRestaurantIntegrationTest` ;
- `ReservationIntegrationTest` ;
- `StockIntegrationTest` ;
- `TableauBordIntegrationTest` ;
- `UtilisateurAdministrationIntegrationTest` ;
- `AuthenticationIntegrationTest` ;
- `SecurityPersistenceIntegrationTest`.

Les tests HTTP ajoutent généralement `@AutoConfigureMockMvc` afin d'exécuter les requêtes contre la pile Spring MVC sans démarrer un serveur HTTP externe.

`GlobalExceptionHandlerTest` n'est pas un test Spring Boot complet. Il utilise un slice `@WebMvcTest` limité au contrôleur de test et au gestionnaire global des exceptions.

`JournalActiviteRepositoryTest` utilise un slice `@DataJpaTest` limité à la couche de persistance.

### 1.5 Tests Testcontainers et PostgreSQL

Onze classes utilisent `@Testcontainers` et un `PostgreSQLContainer` :

- `AchatIntegrationTest` ;
- `CommandeIntegrationTest` ;
- `CuisineCommandeIntegrationTest` ;
- `PaiementIntegrationTest` ;
- `ReferentielsRestaurantIntegrationTest` ;
- `ReservationIntegrationTest` ;
- `StockIntegrationTest` ;
- `TableauBordIntegrationTest` ;
- `UtilisateurAdministrationIntegrationTest` ;
- `AuthenticationIntegrationTest` ;
- `SecurityPersistenceIntegrationTest`.

Ces classes injectent dynamiquement :

- l'URL JDBC du conteneur ;
- le nom d'utilisateur PostgreSQL ;
- le mot de passe généré pour le test ;
- `spring.flyway.enabled=true` ;
- `spring.jpa.hibernate.ddl-auto=validate`.

PostgreSQL est donc réellement utilisé pour vérifier :

- les contraintes uniques et les clés étrangères ;
- l'unicité des e-mails sans tenir compte de la casse ;
- les transactions ;
- les verrouillages optimistes et pessimistes ;
- les scénarios concurrents ;
- l'idempotence des paiements, mouvements de stock et réceptions ;
- les requêtes et agrégations destinées aux tableaux de bord ;
- les migrations et l'initialisation des rôles et permissions.

### 1.6 Validation des migrations Flyway

Le projet contient les migrations suivantes :

- V1 : sécurité, employés et utilisateurs ;
- V2 : rôles et permissions initiaux ;
- V3 : référentiels du restaurant ;
- V4 : commandes ;
- V5 : flux cuisine ;
- V6 : paiements et factures ;
- V7 : réservations ;
- V8 : ingrédients et stock ;
- V9 : fournisseurs et achats ;
- V10 : permissions et index des tableaux de bord ;
- V11 : index de l'historique des mouvements ;
- V12 : disponibilité et images des produits.

Pendant les tests d'intégration, Flyway applique V1 à V12 sur une base PostgreSQL isolée. Hibernate utilise ensuite `ddl-auto=validate`, ce qui vérifie que le schéma obtenu correspond aux entités JPA sans demander à Hibernate de créer automatiquement le schéma.

### 1.7 Rôle de H2

Le profil `application-test.yml` configure une base H2 en mémoire en mode de compatibilité PostgreSQL :

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:restaurant-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
  jpa:
    hibernate:
      ddl-auto: none
  flyway:
    enabled: false
```

H2 est utilisé pour les tests légers de démarrage et de santé qui ne nécessitent pas le modèle PostgreSQL complet.

`JournalActiviteRepositoryTest` remplace localement cette configuration par `ddl-auto=create-drop` tout en conservant Flyway désactivé. Ce test vérifie une requête repository ciblée avec H2, mais les validations complètes du schéma et des transactions restent assurées par les tests PostgreSQL/Testcontainers.

### 1.8 Utilisation directe de Mockito

Mockito est réellement employé dans le code des tests :

- `SessionSecurityValidationFilterTest` utilise `mock`, `when`, `verify` et `never` ;
- `SecurityInputValidationTest` crée des services simulés avec `mock(...)` ;
- `GlobalExceptionHandlerTest` utilise `@MockitoBean`, fourni par Spring Test et fondé sur Mockito.

Il serait donc inexact de présenter Mockito comme une dépendance uniquement transitive et non utilisée.

### 1.9 Domaines fonctionnels couverts

Les tests backend couvrent :

- le démarrage de l'application ;
- la configuration de production ;
- le contrôleur de santé ;
- la protection d'Actuator et d'OpenAPI ;
- l'authentification et la déconnexion ;
- les sessions, CSRF et CORS ;
- la limitation des tentatives de connexion ;
- l'administration des utilisateurs ;
- les rôles et permissions ;
- la protection du dernier administrateur actif ;
- les employés, clients, tables, catégories et produits ;
- la validation et le stockage des images ;
- les commandes, prix, sous-totaux et totaux ;
- la propriété des commandes des serveurs ;
- la concurrence sur les commandes ;
- la préparation en cuisine ;
- les paiements fractionnés et les factures ;
- les réservations et les conflits de créneaux ;
- les ingrédients, recettes et mouvements de stock ;
- les fournisseurs, achats et réceptions ;
- les tableaux de bord ;
- le journal d'activité ;
- la validation Bean Validation ;
- la gestion globale des erreurs.

### 1.10 Tests liés à la sécurité

Les tests de sécurité vérifient notamment :

- une connexion réussie ;
- une erreur publique identique pour un utilisateur inconnu, inactif ou un mot de passe incorrect ;
- la création et l'invalidation de la session ;
- la présence obligatoire du jeton CSRF ;
- le comportement CORS ;
- la déconnexion ;
- la journalisation de l'authentification ;
- la révocation de session après un changement de mot de passe, rôle ou statut ;
- la limitation des tentatives de connexion ;
- les réponses `401` sans authentification ;
- les réponses `403` sans permission ;
- la protection d'Actuator et de la documentation OpenAPI ;
- l'absence de hash de mot de passe dans les réponses ;
- la protection du dernier administrateur actif.

### 1.11 Contrôleurs, services, validations et erreurs

- Les contrôleurs sont principalement testés par MockMvc dans les classes d'intégration.
- Les services métier sont majoritairement exercés indirectement par les tests d'intégration HTTP complets.
- `ProduitImageStorageService` possède un test unitaire dédié.
- Les contraintes des DTO et des paramètres de contrôleurs sont testées par `SecurityInputValidationTest`.
- `GlobalExceptionHandlerTest` vérifie le format commun des erreurs de validation et l'absence de détails internes dans les erreurs inattendues.

## 2. Analyse des tests frontend

### 2.1 Vitest

Le script déclaré dans `frontend/package.json` est :

```json
"test": "vitest run"
```

La configuration `frontend/vitest.config.ts` définit :

```ts
test: {
  environment: 'node',
  setupFiles: './src/test/setup.ts',
  clearMocks: true,
  pool: 'forks',
  maxWorkers: 1,
  fileParallelism: false,
}
```

Les tests s'exécutent donc séquentiellement avec un seul worker et des mocks nettoyés automatiquement.

### 2.2 Environnement DOM

Vitest est configuré avec `environment: 'node'`. Le projet n'utilise pas directement `environment: 'happy-dom'`.

Le fichier `frontend/src/test/setup.ts` enregistre manuellement Happy DOM :

```ts
GlobalRegistrator.register({ url: 'http://localhost:5173' });
```

Le setup :

- fournit les objets DOM dans l'environnement Node ;
- ajoute des matchers personnalisés ;
- nettoie le DOM entre les tests ;
- vide `localStorage` et `sessionStorage` ;
- retire l'environnement Happy DOM à la fin de la suite.

### 2.3 Testing Library

`@testing-library/jest-dom` est déclaré comme dépendance de développement, mais aucun fichier source ne l'importe directement.

Le projet ne déclare pas `@testing-library/react` ni `@testing-library/user-event`.

Les tests utilisent une couche personnalisée dans `frontend/src/test/dom.tsx`, qui fournit une API inspirée de Testing Library :

- `render` ;
- `screen` ;
- `within` ;
- `getByRole` et `findByRole` ;
- `getByText` et `findByText` ;
- `getByLabelText` ;
- `waitFor` ;
- `userEvent` personnalisé.

Le rendu utilise directement `createRoot` et `act` de React.

Il serait donc inexact d'écrire que les tests utilisent `@testing-library/react`. Ils suivent une approche de recherche accessible similaire, mais l'implémentation est interne au projet.

### 2.4 Tests de composants et interactions

Les tests frontend vérifient :

- le rendu des composants et pages ;
- les clics ;
- la saisie dans les formulaires ;
- la sélection d'options ;
- le téléversement de fichiers ;
- la gestion du focus ;
- les dialogues de confirmation ;
- les états de chargement, succès et erreur ;
- la prévention des doubles soumissions ;
- le téléchargement des factures PDF.

### 2.5 Tests de permissions et routes

Les tests rendent fréquemment les vrais composants suivants :

- `AuthProvider` ;
- `RouterProvider` ;
- `AppRoutes` ;
- le composant fonctionnel concerné.

Ils vérifient :

- la redirection vers la connexion sans session ;
- l'affichage d'une page 403 pour une route connue mais interdite ;
- l'affichage d'une page 404 ;
- l'adaptation du menu aux permissions ;
- l'absence d'appel API lorsqu'une permission manque ;
- la redirection après expiration de session.

### 2.6 Mocks des appels API

Les tests de pages utilisent principalement :

```ts
vi.hoisted(...)
vi.fn()
vi.mock(...)
mockResolvedValue(...)
mockRejectedValue(...)
```

Les composants React restent réels, tandis que les modules API sont remplacés par des mocks.

Le test `frontend/src/api/client.test.ts` adopte une approche différente : il espionne `globalThis.fetch` et retourne de vrais objets `Response` simulés. Il vérifie notamment :

- `credentials: 'include'` ;
- l'ajout du jeton CSRF ;
- l'absence de stockage du jeton dans `localStorage` ;
- les formulaires multipart ;
- la traduction des erreurs HTTP ;
- l'expiration de session ;
- le téléchargement de PDF.

### 2.7 Recherche des éléments du DOM

Les tests privilégient :

- les rôles accessibles et leurs noms ;
- les textes visibles ;
- les labels des champs ;
- les requêtes asynchrones `findBy*` ;
- les vérifications négatives `queryBy*`.

Le helper personnalisé normalise les espaces. Lorsqu'une chaîne est fournie, il exige cependant une égalité textuelle exacte. Cette caractéristique explique l'échec du test de réception lorsque seul le préfixe du message change.

### 2.8 Modules fonctionnels testés

Onze fichiers de tests couvrent :

- le client HTTP sécurisé ;
- le dialogue de confirmation ;
- le routage et l'authentification ;
- l'administration des utilisateurs ;
- les référentiels du restaurant ;
- le serveur, les commandes et les réservations ;
- la cuisine ;
- la caisse et les factures ;
- le stock, les fournisseurs et les achats ;
- les tableaux de bord et le journal ;
- les messages d'erreur sûrs.

## 3. Analyse du test frontend en échec

### 3.1 Test concerné

Fichier :

```text
frontend/src/features/stock/InterfaceStock.test.tsx
```

Nom du test :

```text
affiche une erreur de concurrence lors d’une réception
```

### 3.2 Mock de l'API

Le test remplace entièrement `stockApi` par `stockMocks` et configure la réception pour rejeter une promesse :

```ts
stockMocks.recevoirAchat.mockRejectedValue(
  new ApiError(
    409,
    'BUSINESS_CONFLICT',
    'La quantité dépasse le restant à recevoir.',
  ),
);
```

Le statut simulé est donc `409`, le code est `BUSINESS_CONFLICT` et le message métier est :

```text
La quantité dépasse le restant à recevoir.
```

### 3.3 Réponse HTTP simulée

Ce test ne construit pas de véritable objet `Response` et n'appelle pas `fetch`.

Comme le module `stockApi` est mocké, le test simule directement le résultat déjà transformé du client HTTP : une promesse rejetée avec une instance d'`ApiError`.

Dans le fonctionnement réel, l'endpoint est :

```text
POST /api/achats/{id}/receptions
Idempotency-Key: UUID
```

Le client HTTP transforme une réponse non réussie en `ApiError` en conservant :

- le statut HTTP ;
- le code public ;
- le message public ;
- les erreurs de validation.

Cette conversion réelle du client n'est pas traversée par le test problématique, puisque l'`ApiError` y est construite directement.

### 3.4 Gestion de l'erreur dans le composant

`AchatsPage.tsx` contient le formateur suivant :

```ts
const messageOperation = (error: unknown) =>
  error instanceof ApiError && error.status === 409
    ? `Cet achat vient d’être modifié ou l’opération a déjà été traitée : ${error.message}`
    : messageErreur(error);
```

Le déroulement est le suivant :

1. le test prépare une réception partielle ;
2. l'utilisateur confirme la réception ;
3. `recevoirAchat` rejette la promesse ;
4. la fonction `executer` intercepte l'erreur ;
5. `messageOperation` construit le message visible ;
6. `erreurOperation` est mis à jour ;
7. le dialogue de confirmation est fermé ;
8. le message est affiché dans un paragraphe avec `role="alert"`.

### 3.5 Texte attendu et texte réel

Texte attendu par le test :

```text
Conflit de concurrence ou opération répétée : La quantité dépasse le restant à recevoir.
```

Texte réellement généré par le composant :

```text
Cet achat vient d’être modifié ou l’opération a déjà été traitée : La quantité dépasse le restant à recevoir.
```

Le détail métier est identique. Seul le préfixe du message diffère.

### 3.6 Cause exacte de l'échec

Le helper `findByText` utilise une égalité exacte pour les chaînes après normalisation des espaces.

Le texte attendu n'existe donc pas dans le DOM. Après expiration du délai d'attente, le helper renvoie l'erreur :

```text
Texte Conflit de concurrence ou opération répétée : La quantité dépasse le restant à recevoir. introuvable.
```

L'application affiche bien une erreur. Le problème ne vient ni de l'absence d'appel au mock, ni du statut 409, ni de la perte du message métier. Il vient uniquement de la divergence entre le préfixe attendu par le test et celui généré par le composant.

### 3.7 Le message a-t-il été modifié récemment ?

Git ne permet pas de le confirmer :

- `AchatsPage.tsx` est actuellement non suivi ;
- `InterfaceStock.test.tsx` est actuellement non suivi ;
- aucun historique ni `git blame` exploitable n'existe pour ces lignes.

Le dépôt permet uniquement de constater que :

- l'ancien préfixe apparaît dans le test ;
- le nouveau préfixe apparaît dans le composant.

Une modification récente est possible, mais elle ne peut pas être présentée comme un fait vérifié.

### 3.8 Test ou implémentation ?

Techniquement, l'échec vient de l'assertion du test, qui n'est pas alignée sur le comportement courant du composant.

L'implémentation :

- reçoit l'erreur ;
- identifie le statut 409 ;
- conserve le message métier ;
- affiche une erreur visible ;
- utilise un préfixe propre au domaine des achats.

Aucun document du dépôt n'impose précisément l'ancien préfixe. En l'état, le test est donc l'élément obsolète, sauf si une exigence fonctionnelle externe impose l'ancien texte.

### 3.9 Correction minimale recommandée

Si le texte actuel du composant est intentionnel, la correction minimale consiste à modifier uniquement l'assertion du test :

```ts
expect(await screen.findByText(
  'Cet achat vient d’être modifié ou l’opération a déjà été traitée : La quantité dépasse le restant à recevoir.',
)).toBeInTheDocument();
```

Si une spécification externe exige l'ancien texte, l'alternative serait de modifier `messageOperation` dans `AchatsPage.tsx`. Le dépôt ne contient toutefois pas de preuve justifiant cette deuxième option.

## 4. Commandes frontend disponibles

Les scripts suivants existent réellement dans `frontend/package.json` :

| Objectif | Commande | Script exécuté |
|---|---|---|
| Exécuter les tests | `npm test` | `vitest run` |
| Tests en surveillance | `npm run test:watch` | `vitest` |
| Vérifier TypeScript | `npm run typecheck` | `tsc -b --pretty false` |
| Lancer le lint | `npm run lint` | `oxlint src --deny-warnings` |
| Générer le build | `npm run build` | `tsc -b && vite build` |

## 5. Commandes de validation à exécuter

Depuis le dossier frontend, dans l'ordre demandé :

```bash
cd "/Users/abdelkaouiabaoubida/Documents/restaurant project/frontend"

# A. Rejouer uniquement le test problématique
npm test -- src/features/stock/InterfaceStock.test.tsx -t "affiche une erreur de concurrence lors d’une réception"

# B. Exécuter toute la suite frontend
npm test

# C. Vérifier TypeScript
npm run typecheck

# D. Exécuter le lint
npm run lint

# E. Générer le build de production
npm run build
```

## A. TESTS BACKEND VÉRIFIÉS

- **Outils :** JUnit 5, AssertJ, Spring Boot Test, MockMvc, Spring Security Test, Mockito, H2 et Testcontainers.
- **Types :** tests unitaires, slices MVC et JPA, tests de démarrage Spring Boot et tests d'intégration HTTP/persistance.
- **Domaines :** sécurité, utilisateurs, référentiels, commandes, cuisine, caisse, réservations, stock, achats, tableaux de bord, audit, validation et gestion des erreurs.
- **Testcontainers/PostgreSQL :** les intégrations utilisent PostgreSQL 17 dans des conteneurs isolés.
- **Flyway :** les migrations V1 à V12 sont appliquées pendant les tests d'intégration, puis validées par Hibernate avec `ddl-auto=validate`.
- **Résultat local vérifié :** 107 tests, 0 échec, 0 erreur, 0 test ignoré et `BUILD SUCCESS`.

## B. TESTS FRONTEND VÉRIFIÉS

- **Outil :** Vitest 4.1.10.
- **Environnement :** Node avec Happy DOM enregistré manuellement.
- **Approche :** rendu React et helpers DOM personnalisés ; pas d'utilisation directe de `@testing-library/react`.
- **Types :** composants, interactions, formulaires, permissions, routes, API, erreurs, focus, fichiers et doubles soumissions.
- **Domaines :** authentification, utilisateurs, référentiels, commandes, réservations, cuisine, caisse, stock, achats, tableaux de bord et audit.
- **État actuel :** la suite ne peut pas être déclarée entièrement réussie, car un test du flux de réception échoue sur une différence de libellé.

## C. PROBLÈME FRONTEND DÉTECTÉ

- **Test :** `affiche une erreur de concurrence lors d’une réception`.
- **Fichier de test :** `frontend/src/features/stock/InterfaceStock.test.tsx`, lignes 235 à 242.
- **Composant :** `frontend/src/features/stock/AchatsPage.tsx`, notamment les lignes 15 à 16, 101 à 107, 143 à 150 et 157.
- **Client HTTP :** `frontend/src/api/client.ts`, lignes 109 à 117.
- **Cause :** le test attend un ancien préfixe alors que le composant génère un libellé différent.
- **Comportement réel :** l'erreur 409 et son détail métier sont correctement propagés et affichés.
- **Correction minimale recommandée :** aligner l'unique assertion du test sur le texte actuel du composant.

## D. COMMANDES DE VALIDATION

```bash
npm test -- src/features/stock/InterfaceStock.test.tsx -t "affiche une erreur de concurrence lors d’une réception"
npm test
npm run typecheck
npm run lint
npm run build
```

## E. TEXTE PROPOSÉ POUR LA SECTION 3.10 DU RAPPORT

### 3.10 — Stratégie de tests et qualité

La validation de l'application a été réalisée localement au moyen de plusieurs niveaux de tests adaptés aux composants du système. Pour le backend, la suite repose sur JUnit 5, Spring Boot Test, MockMvc, Spring Security Test, Mockito et AssertJ. Des tests unitaires vérifient notamment la limitation des tentatives de connexion, la révocation des sessions, la validation des entrées et le stockage sécurisé des images. Des tests ciblés des couches Web et JPA complètent cette couverture, en particulier pour la gestion globale des erreurs et les requêtes du journal d'activité.

Les principaux scénarios métier font l'objet de tests d'intégration exécutés avec Testcontainers et une instance PostgreSQL isolée. Cette approche permet de vérifier le comportement de l'application avec le même type de système de gestion de base de données que celui prévu par l'architecture du projet. Les migrations Flyway V1 à V12 sont appliquées pendant l'initialisation des tests, tandis qu'Hibernate valide la conformité du schéma avec les entités JPA. Les scénarios couvrent notamment l'authentification, les rôles et permissions, les référentiels, les commandes, la préparation en cuisine, les paiements et factures, les réservations, le stock, les achats, les tableaux de bord et la journalisation. La commande locale `./mvnw test` a exécuté 107 tests, avec 0 échec, 0 erreur et 0 test ignoré.

Le frontend est testé avec Vitest dans un environnement Node enrichi par Happy DOM. Les tests rendent les composants React au moyen d'utilitaires internes et simulent les interactions telles que les clics, la saisie, la sélection et le téléversement de fichiers. Les appels aux modules API sont généralement remplacés par des mocks Vitest, alors que le client HTTP est testé séparément à l'aide de réponses `fetch` simulées. Les contrôles portent également sur les routes protégées, les permissions, les validations de formulaires, les dialogues de confirmation, la prévention des doubles soumissions et l'affichage sûr des erreurs.

Au moment de la validation, la suite frontend contient un test en échec dans le module de gestion des stocks. Cet échec ne provient pas d'une absence de traitement de l'erreur, mais d'une différence entre le libellé attendu par le test et celui effectivement affiché par le composant lors d'un conflit de réception. Le diagnostic recommande d'aligner cette assertion sur le comportement courant, puis de rejouer successivement le test ciblé, l'ensemble de la suite Vitest, la vérification TypeScript, le lint et le build Vite.

Enfin, le démarrage local avec Docker Compose a permis de vérifier l'état sain des quatre services constituant l'environnement applicatif : PostgreSQL, backend, frontend et reverse proxy. Ces validations concernent l'environnement local du projet et ne constituent pas une preuve de déploiement en production.

## Vérifications manuelles restantes

- [ ] Valider le libellé fonctionnel souhaité pour l'erreur de réception.
- [ ] Appliquer ensuite la correction minimale choisie.
- [ ] Rejouer le test frontend ciblé.
- [ ] Confirmer que toute la suite frontend réussit.
- [ ] Exécuter la vérification TypeScript.
- [ ] Exécuter le lint.
- [ ] Exécuter le build Vite.
- [ ] Conserver les sorties finales comme preuves pour le rapport.

## Limites de l'analyse

- Aucun fichier source ou fichier de test n'a été corrigé pendant cette analyse.
- La tentative de rejouer le test ciblé depuis l'environnement Codex n'a produit aucun résultat après 90 secondes et a été interrompue. Le diagnostic de l'échec repose sur le flux déterministe du code et concorde avec l'erreur exacte fournie.
- Le résultat backend est confirmé par les rapports Surefire locaux : 107 tests, 0 échec, 0 erreur et 0 test ignoré.
- Les validations décrites sont locales et ne constituent pas une preuve de mise en production.
