import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../../auth/AuthContext';
import { AppRoutes } from '../../routes/AppRoutes';
import { RouterProvider } from '../../routes/Router';
import { render, screen, userEvent, waitFor, within } from '../../test/dom';
import type { UtilisateurAuthentifie } from '../../types/auth';

const authMocks = vi.hoisted(() => ({ me: vi.fn(), login: vi.fn(), logout: vi.fn() }));
const consommationMocks = vi.hoisted(() => ({ consulter: vi.fn(), telechargerPdf: vi.fn() }));

vi.mock('../../api/authApi', () => ({
  authApi: {
    utilisateurCourant: authMocks.me,
    connexion: authMocks.login,
    deconnexion: authMocks.logout,
  },
}));
vi.mock('./consommationApi', () => ({ consommationApi: consommationMocks }));

function utilisateur(role: string): UtilisateurAuthentifie {
  return {
    id: 1,
    email: `${role.toLowerCase()}@restaurant.test`,
    employe: { id: 1, matricule: 'EMP-1', nom: 'Amrani', prenom: 'Sara', poste: role },
    roles: [role],
    permissions: [],
  };
}

function application(role: string, route: string) {
  authMocks.me.mockResolvedValue(utilisateur(role));
  return render(
    <RouterProvider initialPath={route}>
      <AuthProvider><AppRoutes /></AuthProvider>
    </RouterProvider>,
  );
}

describe('consultation des consommations', () => {
  beforeEach(() => {
    Object.values(authMocks).forEach((mock) => mock.mockReset());
    Object.values(consommationMocks).forEach((mock) => mock.mockReset());
    consommationMocks.consulter.mockResolvedValue({
      debut: '2026-08-01',
      fin: '2026-08-31',
      produits: [{ produitId: 10, produit: 'Pizza Margherita', quantite: 12 }],
      ingredients: [{ ingredientId: 15, ingredient: 'Farine', quantite: 3, unite: 'KILOGRAMME' }],
    });
    consommationMocks.telechargerPdf.mockResolvedValue(
      new Blob(['%PDF-1.7'], { type: 'application/pdf' }),
    );
    vi.restoreAllMocks();
  });

  it.each(['ADMINISTRATEUR', 'GERANT'])(
    'affiche le lien et autorise la page pour le rôle %s',
    async (role) => {
      const accueil = application(role, '/');
      await screen.findByRole('heading', { name: 'Bonjour Sara' });
      expect(within(screen.getByRole('navigation', { name: 'Navigation' }))
        .getByRole('link', { name: 'Consommation' })).toBeInTheDocument();
      accueil.unmount();

      application(role, '/consommation');
      expect(await screen.findByRole('heading', { name: 'Consommation' })).toBeInTheDocument();
      expect(await screen.findByText('Pizza Margherita')).toBeInTheDocument();
      expect(screen.getByText('Farine')).toBeInTheDocument();
      expect(screen.getByText('kg')).toBeInTheDocument();
      expect(consommationMocks.consulter).toHaveBeenCalledTimes(1);
    },
  );

  it('masque le lien et refuse la route directe aux autres rôles', async () => {
    const accueil = application('SERVEUR', '/');
    await screen.findByRole('heading', { name: 'Bonjour Sara' });
    expect(within(screen.getByRole('navigation', { name: 'Navigation' }))
      .queryByRole('link', { name: 'Consommation' })).not.toBeInTheDocument();
    accueil.unmount();

    application('SERVEUR', '/consommation');
    expect(await screen.findByRole('heading', { name: 'Accès refusé' })).toBeInTheDocument();
    expect(consommationMocks.consulter).not.toHaveBeenCalled();
  });

  it('valide la période avant de relancer le calcul', async () => {
    application('GERANT', '/consommation');
    const user = userEvent.setup();
    await screen.findByText('Pizza Margherita');
    await user.clear(screen.getByLabelText('Du'));
    await user.click(screen.getByRole('button', { name: 'Appliquer' }));
    expect(screen.getByRole('alert').textContent).toContain('Les deux dates sont obligatoires.');
    expect(consommationMocks.consulter).toHaveBeenCalledTimes(1);
  });

  it('affiche le cas vide sans erreur technique', async () => {
    consommationMocks.consulter.mockResolvedValue({
      debut: '2026-08-01', fin: '2026-08-31', produits: [], ingredients: [],
    });
    application('ADMINISTRATEUR', '/consommation');
    expect(await screen.findByRole('heading', { name: 'Aucune consommation' })).toBeInTheDocument();
    expect(screen.queryByText(/exception|stack trace|sql/i)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Télécharger le rapport PDF' })).toBeInTheDocument();
  });

  it('télécharge le PDF de la période affichée avec un nom professionnel', async () => {
    const createObjectUrl = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:rapport');
    const revokeObjectUrl = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    application('GERANT', '/consommation');
    const user = userEvent.setup();
    await screen.findByText('Pizza Margherita');
    await user.click(screen.getByRole('button', { name: 'Télécharger le rapport PDF' }));

    await waitFor(() => expect(consommationMocks.telechargerPdf)
      .toHaveBeenCalledWith('2026-08-01', '2026-08-31'));
    expect(createObjectUrl).toHaveBeenCalledTimes(1);
    expect(click).toHaveBeenCalledTimes(1);
    expect(revokeObjectUrl).toHaveBeenCalledWith('blob:rapport');
  });
});
