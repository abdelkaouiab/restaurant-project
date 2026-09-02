import { act, render, screen, userEvent, waitFor, within } from '../test/dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, apiRequest } from '../api/client';
import { AuthProvider } from '../auth/AuthContext';
import type { UtilisateurAuthentifie } from '../types/auth';
import { AppRoutes } from './AppRoutes';
import { RouterProvider } from './Router';

const mocks = vi.hoisted(() => ({ me: vi.fn(), login: vi.fn(), logout: vi.fn() }));
const dashboardMocks = vi.hoisted(() => ({ operations: vi.fn(), finances: vi.fn(), journal: vi.fn(), compterUtilisateurs: vi.fn(), commandesAPayer: vi.fn(), commandesCuisine: vi.fn(), compterAchats: vi.fn() }));
vi.mock('../api/authApi', () => ({
  authApi: {
    utilisateurCourant: mocks.me,
    connexion: mocks.login,
    deconnexion: mocks.logout,
  },
}));
vi.mock('../features/dashboard/dashboardApi', () => ({ dashboardApi: dashboardMocks }));

const operationsVides = {
  debut: '2026-08-03', fin: '2026-08-03', nombreCommandes: 0, nombreTablesDisponibles: 0,
  commandesParEtat: [], produitsLesPlusVendus: [], reservations: [], stocksFaibles: [],
};

const baseUtilisateur: UtilisateurAuthentifie = {
  id: 1,
  email: 'gerant@example.test',
  employe: { id: 2, matricule: 'EMP-1', nom: 'Martin', prenom: 'Lina', poste: 'Gérante' },
  roles: ['GERANT'],
  permissions: [],
};

const utilisateurServeur: UtilisateurAuthentifie = {
  ...baseUtilisateur,
  roles: ['SERVEUR'],
  permissions: [
    'TABLEAU_BORD_CONSULTER',
    'EMPLOYE_CONSULTER',
    'CLIENT_CONSULTER',
    'TABLE_CONSULTER',
    'CATEGORIE_CONSULTER',
    'PRODUIT_CONSULTER',
    'COMMANDE_CONSULTER',
    'RESERVATION_GERER',
  ],
};

const utilisateurStock: UtilisateurAuthentifie = {
  ...baseUtilisateur,
  employe: { ...baseUtilisateur.employe, poste: 'Responsable stock' },
  roles: ['RESPONSABLE_STOCK'],
  permissions: [
    'TABLEAU_BORD_CONSULTER',
    'EMPLOYE_CONSULTER',
    'CATEGORIE_CONSULTER',
    'PRODUIT_CONSULTER',
    'STOCK_CONSULTER',
    'ACHAT_CONSULTER',
  ],
};

function application(route: string) {
  return render(<RouterProvider initialPath={route}><AuthProvider><AppRoutes /></AuthProvider></RouterProvider>);
}

describe('navigation et routes sécurisées', () => {
  beforeEach(() => {
    mocks.me.mockReset();
    mocks.login.mockReset();
    mocks.logout.mockReset();
    Object.values(dashboardMocks).forEach((mock) => mock.mockReset());
    dashboardMocks.operations.mockResolvedValue(operationsVides);
    dashboardMocks.finances.mockResolvedValue({ debut: '2026-08-03', fin: '2026-08-03', chiffreAffaires: 0, paiementsParMode: [], depensesAchat: 0 });
    dashboardMocks.journal.mockResolvedValue({ contenu: [], page: 0, taille: 5, totalElements: 0, totalPages: 0 });
    dashboardMocks.compterUtilisateurs.mockResolvedValue(0);
    dashboardMocks.commandesAPayer.mockResolvedValue([]);
    dashboardMocks.commandesCuisine.mockResolvedValue([]);
    dashboardMocks.compterAchats.mockResolvedValue(0);
    vi.restoreAllMocks();
  });

  it('redirige directement une route protégée vers la connexion sans session', async () => {
    mocks.me.mockRejectedValue(new ApiError(401, 'UNAUTHORIZED', 'Authentification requise.'));
    application('/commandes');
    expect(await screen.findByRole('heading', { name: 'Bienvenue' })).toBeInTheDocument();
  });

  it('redirige aussi l’ancienne adresse 401 vers la connexion', async () => {
    mocks.me.mockRejectedValue(new ApiError(401, 'UNAUTHORIZED', 'Authentification requise.'));
    application('/401');
    expect(await screen.findByRole('heading', { name: 'Bienvenue' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Authentification requise' })).not.toBeInTheDocument();
  });

  it('affiche une route autorisée dans le layout authentifié', async () => {
    mocks.me.mockResolvedValue(utilisateurServeur);
    application('/tableau-de-bord');
    expect(await screen.findByRole('heading', { name: 'Bonjour Lina' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Se déconnecter' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Commandes/ })).toBeInTheDocument();
  });

  it('affiche la page 403 pour une route connue mais interdite', async () => {
    mocks.me.mockResolvedValue(utilisateurServeur);
    application('/utilisateurs');
    expect(await screen.findByRole('heading', { name: 'Accès refusé' })).toBeInTheDocument();
  });

  it('affiche la page 404 pour une route inexistante', async () => {
    mocks.me.mockResolvedValue(utilisateurServeur);
    application('/route-inconnue');
    expect(await screen.findByRole('heading', { name: 'Page introuvable' })).toBeInTheDocument();
  });

  it('adapte le menu aux permissions de chaque rôle', async () => {
    mocks.me.mockResolvedValue(utilisateurServeur);
    const premiereApplication = application('/');
    await screen.findByRole('heading', { name: 'Bonjour Lina' });
    let navigation = within(screen.getByRole('navigation', { name: 'Navigation' }));
    expect(navigation.getByRole('link', { name: 'Commandes' })).toBeInTheDocument();
    expect(navigation.getByRole('link', { name: 'Réservations' })).toBeInTheDocument();
    expect(navigation.queryByRole('link', { name: 'Stock' })).not.toBeInTheDocument();
    expect(navigation.queryByRole('link', { name: 'Achats' })).not.toBeInTheDocument();
    premiereApplication.unmount();

    mocks.me.mockResolvedValue(utilisateurStock);
    application('/');
    await screen.findByRole('heading', { name: 'Bonjour Lina' });
    navigation = within(screen.getByRole('navigation', { name: 'Navigation' }));
    expect(navigation.getByRole('link', { name: 'Stock' })).toBeInTheDocument();
    expect(navigation.getByRole('link', { name: 'Fournisseurs' })).toBeInTheDocument();
    expect(navigation.getByRole('link', { name: 'Achats' })).toBeInTheDocument();
    expect(navigation.queryByRole('link', { name: 'Commandes' })).not.toBeInTheDocument();
    expect(navigation.queryByRole('link', { name: 'Réservations' })).not.toBeInTheDocument();
  });

  it('redirige vers la connexion quand une requête signale une session expirée', async () => {
    mocks.me.mockResolvedValue(utilisateurServeur);
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(
      JSON.stringify({ code: 'UNAUTHORIZED', message: 'Authentification requise.' }),
      { status: 401, headers: { 'Content-Type': 'application/json' } },
    ));
    application('/tableau-de-bord');
    await screen.findByRole('heading', { name: 'Bonjour Lina' });

    await act(async () => {
      await expect(apiRequest('/api/test-session')).rejects.toMatchObject({ status: 401 });
    });

    expect(await screen.findByRole('heading', { name: 'Bienvenue' })).toBeInTheDocument();
  });

  it('connecte avec le formulaire sans persister le mot de passe', async () => {
    mocks.me.mockRejectedValue(new ApiError(401, 'UNAUTHORIZED', ''));
    mocks.login.mockResolvedValue(utilisateurServeur);
    application('/connexion');
    const user = userEvent.setup();
    await screen.findByRole('heading', { name: 'Bienvenue' });
    await user.type(screen.getByLabelText('Adresse e-mail'), 'serveur@example.test');
    await user.type(screen.getByLabelText('Mot de passe'), 'Secret-temporaire');
    await user.click(screen.getByRole('button', { name: 'Se connecter' }));
    expect(await screen.findByRole('heading', { name: 'Bonjour Lina' })).toBeInTheDocument();
    expect(mocks.login).toHaveBeenCalledWith({ email: 'serveur@example.test', motDePasse: 'Secret-temporaire' });
    await waitFor(() => expect(screen.queryByLabelText('Mot de passe')).not.toBeInTheDocument());
    expect(localStorage.length).toBe(0);
  });
});
