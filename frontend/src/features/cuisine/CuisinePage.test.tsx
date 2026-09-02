import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api/client';
import { AuthProvider } from '../../auth/AuthContext';
import { AppRoutes } from '../../routes/AppRoutes';
import { RouterProvider } from '../../routes/Router';
import { render, screen, userEvent, waitFor, within } from '../../test/dom';
import type { UtilisateurAuthentifie } from '../../types/auth';
import type { CommandeCuisine } from './types';

const authMocks = vi.hoisted(() => ({ me: vi.fn(), login: vi.fn(), logout: vi.fn() }));
const cuisineMocks = vi.hoisted(() => ({ lister: vi.fn(), demarrer: vi.fn(), marquerPrete: vi.fn() }));

vi.mock('../../api/authApi', () => ({ authApi: { utilisateurCourant: authMocks.me, connexion: authMocks.login, deconnexion: authMocks.logout } }));
vi.mock('./cuisineApi', () => ({ cuisineApi: cuisineMocks }));

const confirmee: CommandeCuisine = {
  numero: 'CMD-KITCHEN-001', table: 7,
  produits: [{ produit: 'Tajine légumes', quantite: 2, remarque: 'Sans oignon' }],
  heure: '2026-08-03T17:30:00Z', etat: 'CONFIRMEE',
};
const enPreparation: CommandeCuisine = {
  ...confirmee, numero: 'CMD-KITCHEN-002', table: 4, etat: 'EN_PREPARATION',
  produits: [{ ...confirmee.produits[0], remarque: null }],
};
const prete: CommandeCuisine = {
  ...confirmee, numero: 'CMD-KITCHEN-003', table: 9, etat: 'PRETE',
  produits: [{ ...confirmee.produits[0], remarque: null }],
};

function utilisateurCuisine(): UtilisateurAuthentifie {
  return {
    id: 8, email: 'cuisine@restaurant.test',
    employe: { id: 8, matricule: 'CUI-8', nom: 'Alaoui', prenom: 'Samira', poste: 'Cuisine' },
    roles: ['CUISINE'], permissions: ['TABLEAU_BORD_CONSULTER', 'CUISINE_COMMANDES_GERER'],
  };
}

function application(route = '/cuisine', utilisateur = utilisateurCuisine()) {
  authMocks.me.mockResolvedValue(utilisateur);
  return render(<RouterProvider initialPath={route}><AuthProvider><AppRoutes /></AuthProvider></RouterProvider>);
}

describe('interface cuisine', () => {
  beforeEach(() => {
    Object.values(authMocks).forEach((mock) => mock.mockReset());
    Object.values(cuisineMocks).forEach((mock) => mock.mockReset());
    cuisineMocks.lister.mockResolvedValue([confirmee, enPreparation, prete]);
  });

  it('autorise le rôle CUISINE et affiche les commandes prêtes récentes', async () => {
    application();
    expect(await screen.findByRole('heading', { name: 'Commandes à préparer' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'À démarrer' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'En préparation' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Prêtes récemment' })).toBeInTheDocument();
    expect(screen.getByText('CMD-KITCHEN-003')).toBeInTheDocument();
    expect(screen.queryByText(/L’historique court des commandes prêtes n’est pas affiché/)).not.toBeInTheDocument();
    expect(screen.getByText('Sans oignon')).toBeInTheDocument();
  });

  it('refuse la route à un utilisateur sans permission cuisine', async () => {
    application('/cuisine', {
      ...utilisateurCuisine(), roles: ['SERVEUR'], permissions: ['COMMANDE_CONSULTER'],
    });
    expect(await screen.findByRole('heading', { name: 'Accès refusé' })).toBeInTheDocument();
    expect(cuisineMocks.lister).not.toHaveBeenCalled();
  });

  it('n’affiche aucune donnée financière, de paiement ou personnelle', async () => {
    cuisineMocks.lister.mockResolvedValue([{
      ...confirmee,
      prix: '999,99', total: '1234,56', paiement: 'PAIEMENT_SECRET',
      facture: 'FACTURE_SECRETE', client: 'CLIENT_SECRET', email: 'EMAIL_SECRET',
    } as unknown as CommandeCuisine]);
    application();
    await screen.findByText('Tajine légumes');
    expect(screen.queryByText('999,99')).not.toBeInTheDocument();
    expect(screen.queryByText('1234,56')).not.toBeInTheDocument();
    expect(screen.queryByText('PAIEMENT_SECRET')).not.toBeInTheDocument();
    expect(screen.queryByText('FACTURE_SECRETE')).not.toBeInTheDocument();
    expect(screen.queryByText('CLIENT_SECRET')).not.toBeInTheDocument();
    expect(screen.queryByText('EMAIL_SECRET')).not.toBeInTheDocument();
  });

  it('applique la transition CONFIRMEE vers EN_PREPARATION', async () => {
    cuisineMocks.lister.mockResolvedValue([confirmee]);
    cuisineMocks.demarrer.mockResolvedValue({ ...confirmee, etat: 'EN_PREPARATION' });
    application();
    const user = userEvent.setup();
    await screen.findByText('CMD-KITCHEN-001');
    await user.click(screen.getByRole('button', { name: 'Démarrer la préparation' }));
    await waitFor(() => expect(cuisineMocks.demarrer).toHaveBeenCalledWith('CMD-KITCHEN-001'));
    expect(await screen.findByRole('button', { name: 'Marquer prête' })).toBeInTheDocument();
  });

  it('ne propose jamais une transition invalide pour l’état affiché', async () => {
    cuisineMocks.lister.mockResolvedValue([enPreparation]);
    application();
    await screen.findByText('CMD-KITCHEN-002');
    expect(screen.queryByRole('button', { name: 'Démarrer la préparation' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Marquer prête' })).toBeInTheDocument();
  });

  it('déplace une commande terminée dans l’historique prêt sans proposer une nouvelle action', async () => {
    cuisineMocks.lister.mockResolvedValue([enPreparation]);
    cuisineMocks.marquerPrete.mockResolvedValue({ ...enPreparation, etat: 'PRETE' });
    application();
    const user = userEvent.setup();
    await screen.findByText('CMD-KITCHEN-002');
    await user.click(screen.getByRole('button', { name: 'Marquer prête' }));
    await waitFor(() => expect(cuisineMocks.marquerPrete).toHaveBeenCalledWith('CMD-KITCHEN-002'));
    const colonnePrete = screen.getByRole('heading', { name: 'Prêtes récemment' }).closest('section');
    expect(colonnePrete).not.toBeNull();
    expect(await within(colonnePrete!).findByText('CMD-KITCHEN-002')).toBeInTheDocument();
    expect(within(colonnePrete!).queryByRole('button')).not.toBeInTheDocument();
  });

  it('affiche correctement une erreur concurrente 409 et resynchronise la liste', async () => {
    cuisineMocks.lister.mockResolvedValue([enPreparation]);
    cuisineMocks.marquerPrete.mockRejectedValue(new ApiError(409, 'BUSINESS_CONFLICT', 'Seule une commande en préparation peut être marquée prête.'));
    application();
    const user = userEvent.setup();
    await screen.findByText('CMD-KITCHEN-002');
    await user.click(screen.getByRole('button', { name: 'Marquer prête' }));
    expect(await screen.findByText('Cette commande vient d’être modifiée : Seule une commande en préparation peut être marquée prête.')).toBeInTheDocument();
    await waitFor(() => expect(cuisineMocks.lister).toHaveBeenCalledTimes(2));
  });

  it('signale une nouvelle commande détectée lors d’une actualisation manuelle', async () => {
    cuisineMocks.lister.mockResolvedValueOnce([enPreparation]).mockResolvedValueOnce([enPreparation, confirmee]);
    application();
    const user = userEvent.setup();
    await screen.findByText('CMD-KITCHEN-002');
    await user.click(screen.getByRole('button', { name: 'Actualiser' }));
    expect(await screen.findByText('Nouvelle')).toBeInTheDocument();
    expect(screen.getByText('1 nouvelle commande')).toBeInTheDocument();
  });
});
