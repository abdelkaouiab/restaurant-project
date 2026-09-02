# Fondation frontend React — étape 13

## Technologies

- React 19 ;
- Vite 8 ;
- TypeScript strict ;
- Fetch natif avec cookies de session ;
- routeur client minimal fondé sur l'API History ;
- Vitest, Testing Library et jsdom.

Le routeur minimal évite une dépendance présentant des alertes de sécurité npm
contradictoires au moment de cette étape. Il couvre uniquement les routes du
socle et pourra être remplacé lors d'une décision d'architecture explicite si
les besoins de navigation deviennent plus complexes.

## Architecture

```text
frontend/src/
├── api/          # client HTTP, CSRF et contrat d'authentification
├── auth/         # contexte de session et gardes
├── components/   # composants partagés et contrôle visuel des permissions
├── features/     # réservé aux futures fonctionnalités métier
├── layouts/      # layout authentifié
├── pages/        # connexion, accueil neutre, 401, 403 et 404
├── routes/       # définition des routes et API History
├── types/        # contrats TypeScript
└── utils/        # fonctions de rôles et permissions
```

## Sécurité

- chaque requête utilise `credentials: "include"` ;
- le jeton CSRF est obtenu par `GET /api/auth/csrf` et conservé seulement en mémoire ;
- les écritures placent le jeton dans l'en-tête indiqué par le backend ;
- un refus CSRF provoque un seul renouvellement et une seule nouvelle tentative ;
- aucun mot de passe, jeton ou cookie n'est écrit dans `localStorage` ou `sessionStorage` ;
- aucun usage de `dangerouslySetInnerHTML` ;
- les gardes et masquages visuels améliorent l'expérience mais ne remplacent jamais `@PreAuthorize` et les contrôles backend.

## Développement

Le serveur Vite écoute sur `http://localhost:5173` et relaie `/api` vers
`http://localhost:8080`. En déploiement séparé, `VITE_API_BASE_URL` doit être
une origine autorisée par la liste CORS du backend.

```bash
cd frontend
npm install
npm run dev
```

Vérification :

```bash
npm test
npm run build
npm audit
```
