# Diagnostic technique — démarrage des workers Vitest

## CAUSE LA PLUS PROBABLE

- Le frontend et ses dépendances ont été déchargés par macOS/File Provider. Les fichiers portent les attributs `compressed,dataless` : ils existent logiquement, mais leur contenu n’est pas stocké localement.
- Vitest doit rapatrier de nombreux modules avant que son worker puisse répondre. Son délai interne de démarrage est de 60 secondes ; certains imports dépassent largement cette limite.
- Le disque est rempli à 97 %, avec seulement 7,9 Gio disponibles, ce qui favorise ce déchargement.
- Ce n’est pas une incompatibilité déclarée entre Node, Vitest, Vite, TypeScript ou Happy DOM.

## PREUVES

- Versions réellement installées :
  - Node actif : `v24.10.0`, ARM64, `/opt/homebrew/Cellar/node/24.10.0/bin/node`
  - autre Node installé : `v24.14.1`, `/usr/local/bin/node`
  - npm : `11.6.0`
  - Vitest : `4.1.10`
  - Vite : `8.2.0`
  - React et React DOM : `19.2.8`
  - TypeScript : `7.0.2`
  - Happy DOM et Global Registrator : `20.8.9`
  - plugin React Vite : `6.0.5`
- Contraintes déclarées satisfaites :
  - Vitest accepte Node `^20 || ^22 || >=24`.
  - Vite accepte Node `^20.19 || >=22.12`.
  - Happy DOM exige Node `>=20`.
  - Vitest 4.1.10 accepte Vite 8.
  - Le lockfile de version 3 correspond exactement aux versions installées.
- Les mécanismes Node fonctionnent normalement :
  - création d’un processus enfant avec IPC : `32 ms` ;
  - création d’un worker thread : `38 ms`.
- Le chemin contenant un espace fonctionne :
  - Node le convertit correctement en URL avec `restaurant%20project` ;
  - les entrypoints Vitest sont résolus et importés ;
  - le processus enfant démarre bien depuis ce chemin.
- État du stockage :
  - `node_modules` contient `5 426` fichiers ;
  - les `5 426` sont marqués `dataless` ;
  - le frontend contient `93` fichiers hors `node_modules` ;
  - les `93` sont également `dataless` ;
  - taille logique de `node_modules` : `117M` ;
  - occupation physique : `0B`.
- Exemples vérifiés :
  - `node_modules/vitest/dist/workers/forks.js` : `compressed,dataless`, 0 bloc ;
  - `node_modules/vitest/dist/chunks/base.B6Opl8PE.js` : `compressed,dataless`, 0 bloc ;
  - `node_modules/happy-dom/lib/index.js` : `compressed,dataless`, 0 bloc ;
  - `node_modules/@happy-dom/global-registrator/lib/index.js` : `compressed,dataless`, 0 bloc.
- Import direct du runtime Vitest :
  - premier import de `forks.js` : `20,08 s`, dont seulement `0,18 s` de CPU ;
  - premier import de `threads.js` : `89,98 s`, dont seulement `0,26 s` de CPU ;
  - le timeout interne du worker est de `60 s`.
- Import direct de `@happy-dom/global-registrator`, sans Vitest ni test métier :
  - toujours bloqué après `200,75 s` ;
  - seulement `0,31 s` de CPU ;
  - cela reproduit le ralentissement hors de Vitest.
- La commande ciblée a finalement réussi lorsque les lectures ont abouti :
  - `Test Files 1 passed (1)` ;
  - `Tests 10 passed (10)` ;
  - durée : `797,14 s` ;
  - transform : `90,05 s` ;
  - setup : `686,91 s` ;
  - import : `100,71 s` ;
  - tests réels : `357 ms`.
- `maxWorkers: 1` et `fileParallelism: false` ne désactivent pas le worker : ils imposent seulement une exécution séquentielle.
- Le pool `threads` rencontre le même problème parce qu’il doit lire les mêmes fichiers déchargés.
- `setup.ts` et l’enregistrement manuel de Happy DOM amplifient le nombre de modules à charger, mais ne constituent pas une erreur de configuration. Changer pour `environment: "happy-dom"` chargerait toujours Happy DOM.
- Aucun crash silencieux n’a été trouvé : le worker observé restait vivant, endormi et à `0 %` CPU.

## COMMANDES DE DIAGNOSTIC

### 1. Vérifier l’état `dataless` et l’espace disque

```bash
cd "/Users/abdelkaouiabaoubida/Documents/restaurant project/frontend"

df -h .

find node_modules -type f -flags +dataless | wc -l
find . -path ./node_modules -prune -o -type f -flags +dataless -print | wc -l

du -sh node_modules
du -shA node_modules
```

- vérifie : fichiers déchargés et différence entre taille physique et logique ;
- résultat actuel : `5426`, `93`, `0B` physique et `117M` logique ;
- résultat attendu après correction : zéro fichier `dataless` et une taille physique non nulle ;
- si les compteurs restent supérieurs à zéro : ne pas modifier Vitest ; les fichiers ne sont toujours pas matérialisés.

### 2. Vérifier directement le chargement du worker

```bash
cd "/Users/abdelkaouiabaoubida/Documents/restaurant project/frontend"

/usr/bin/time -p node node_modules/vitest/dist/workers/forks.js
/usr/bin/time -p node node_modules/vitest/dist/workers/threads.js
```

- vérifie : temps de chargement du runtime Vitest sans test métier ;
- les erreurs `Expected worker to be run in node:child_process` et `Expected worker to be run in node:worker_threads` sont normales dans ce diagnostic ;
- résultat attendu après matérialisation : chargement en quelques secondes ;
- si un import dépasse encore 60 secondes : le stockage ou le fournisseur cloud reste bloquant.

### 3. Confirmer la cause dans une copie temporaire locale

```bash
vitest_diag_dir="$(mktemp -d /private/tmp/restaurant-frontend-vitest.XXXXXX)"

rsync -a \
  --exclude node_modules \
  --exclude dist \
  "/Users/abdelkaouiabaoubida/Documents/restaurant project/frontend/" \
  "$vitest_diag_dir/"

cd "$vitest_diag_dir"
npm ci
npm test -- src/features/service/InterfaceServeur.test.tsx
```

- vérifie : exécution depuis un stockage local non géré par File Provider ;
- résultat attendu : `1 passed` et `10 passed` dans un délai normal ;
- si cela réussit : la causalité du stockage `dataless` est confirmée ;
- si cela échoue encore avec un timeout worker : vérifier ensuite Node, l’installation npm ou Vitest.

### 4. Correction environnementale minimale dans l’emplacement actuel

- libérer de l’espace disque avec une sélection manuelle et sûre ;
- dans Finder, appliquer « Télécharger » ou « Toujours conserver téléchargé » au dossier :

```text
/Users/abdelkaouiabaoubida/Documents/restaurant project/frontend
```

Puis vérifier :

```bash
cd "/Users/abdelkaouiabaoubida/Documents/restaurant project/frontend"

df -h .
find node_modules -type f -flags +dataless | wc -l
find . -path ./node_modules -prune -o -type f -flags +dataless -print | wc -l
```

- résultat attendu : espace disque sensiblement supérieur aux 7,9 Gio actuels et deux compteurs à zéro ;
- si les fichiers restent `dataless` : déplacer le frontend vers un répertoire local non synchronisé, puis recréer `node_modules`.

### 5. Solution plus intrusive si la matérialisation sur place ne tient pas

```bash
mkdir -p "/Users/abdelkaouiabaoubida/Developer/restaurant-frontend"

rsync -a \
  --exclude node_modules \
  --exclude dist \
  "/Users/abdelkaouiabaoubida/Documents/restaurant project/frontend/" \
  "/Users/abdelkaouiabaoubida/Developer/restaurant-frontend/"

cd "/Users/abdelkaouiabaoubida/Developer/restaurant-frontend"
npm ci
npm test -- src/features/service/InterfaceServeur.test.tsx
```

- vérifie et corrige : exécution depuis un emplacement non déchargé par le fournisseur cloud ;
- résultat attendu : `10 passed` dans un délai normal ;
- si le timeout persiste avec zéro fichier `dataless` : effectuer alors seulement un test A/B avec Node 22 LTS.

## CORRECTION MINIMALE RECOMMANDÉE

- Libérer de l’espace disque, puis demander à macOS de conserver localement tout le dossier `frontend`.
- Vérifier que les compteurs `dataless` tombent à zéro.
- Ne modifier ni `vitest.config.ts`, ni `setup.ts`, ni les tests métier.
- Si macOS décharge de nouveau les fichiers, travailler depuis un répertoire non synchronisé comme `/Users/abdelkaouiabaoubida/Developer/restaurant-frontend`.
- Ne changer de version de Node qu’en dernier recours : Node 24.10.0 est officiellement accepté par toutes les dépendances installées.

## FICHIERS QUI DEVRAIENT ÊTRE MODIFIÉS

- aucun fichier source ou de configuration ;
- `node_modules` peut être recréé avec `npm ci` après matérialisation ou déplacement.

## COMMANDE DE VALIDATION FINALE

```bash
cd "/Users/abdelkaouiabaoubida/Documents/restaurant project/frontend"
npm test -- src/features/service/InterfaceServeur.test.tsx
```

## LIMITES ET ÉTAT DE L’INTERVENTION

- Aucun fichier frontend ou de configuration n’a été modifié pendant le diagnostic.
- Le backend, le rapport Word, Docker et la base de données n’ont pas été touchés.
- Aucun commit Git n’a été créé.
