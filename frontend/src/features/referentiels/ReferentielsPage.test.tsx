import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api/client';
import { AuthProvider } from '../../auth/AuthContext';
import { AppRoutes } from '../../routes/AppRoutes';
import { RouterProvider } from '../../routes/Router';
import { render, screen, userEvent, waitFor, within } from '../../test/dom';
import type { UtilisateurAuthentifie } from '../../types/auth';
import type { Categorie, Employe, Produit, TableRestaurant } from './types';

const authMocks = vi.hoisted(() => ({ me: vi.fn(), login: vi.fn(), logout: vi.fn() }));
const apiMocks = vi.hoisted(() => ({
  rechercher: vi.fn(), obtenir: vi.fn(), creer: vi.fn(), modifier: vi.fn(),
  modifierStatut: vi.fn(), enregistrerImageProduit: vi.fn(), supprimerImageProduit: vi.fn(),
  listerCategoriesActives: vi.fn(),
}));

vi.mock('../../api/authApi', () => ({ authApi: { utilisateurCourant: authMocks.me, connexion: authMocks.login, deconnexion: authMocks.logout } }));
vi.mock('./referentielsApi', () => ({ referentielsApi: apiMocks }));

const employe: Employe = {
  id: 4, matricule: 'EMP-004', nom: 'Bennani', prenom: 'Sara', telephone: '+212 600 000 000',
  poste: 'Serveuse', dateEmbauche: '2025-02-03', actif: true,
  creeLe: '2025-02-03T10:00:00Z', modifieLe: '2025-02-03T10:00:00Z', version: 0,
};
const categorie: Categorie = {
  id: 2, nom: 'Boissons', description: 'Boissons fraîches', actif: true,
  creeLe: '2025-01-01T10:00:00Z', modifieLe: '2025-01-01T10:00:00Z', version: 0,
};
const table: TableRestaurant = {
  id: 8, numero: 12, capacite: 4, actif: true,
  statutOperationnel: 'OCCUPEE', disponible: false,
  commandeAssociee: { id: 14, numero: 'CMD-014', statut: 'EN_PREPARATION' },
  reservationEnCours: {
    id: 18, numero: 'RES-018', statut: 'ARRIVEE', nombrePersonnes: 4,
    debut: '2026-08-04T18:00:00Z', fin: '2026-08-04T20:00:00Z',
  },
  creeLe: '2025-01-01T10:00:00Z', modifieLe: '2025-01-01T10:00:00Z', version: 0,
};
const produit: Produit = {
  id: 9, nom: 'Thé à la menthe', description: 'Thé frais', prix: 18,
  disponible: true, imageUrl: '/api/produits/9/image', imageTypeMime: 'image/png',
  categorie, actif: true, creeLe: '2025-01-01T10:00:00Z', modifieLe: '2025-01-01T10:00:00Z', version: 0,
};

function page<T>(contenu: T[]) {
  return { contenu, page: 0, taille: 10, totalElements: contenu.length, totalPages: contenu.length ? 1 : 0 };
}

function utilisateur(permissions: string[]): UtilisateurAuthentifie {
  return {
    id: 1, email: 'admin@restaurant.test',
    employe: { id: 1, matricule: 'ADM-1', nom: 'Admin', prenom: 'Amina', poste: 'Administration' },
    roles: ['ADMINISTRATEUR'], permissions,
  };
}

function application(route: string, permissions: string[]) {
  authMocks.me.mockResolvedValue(utilisateur(permissions));
  return render(<RouterProvider initialPath={route}><AuthProvider><AppRoutes /></AuthProvider></RouterProvider>);
}

describe('données principales du restaurant', () => {
  beforeEach(() => {
    Object.values(authMocks).forEach((mock) => mock.mockReset());
    Object.values(apiMocks).forEach((mock) => mock.mockReset());
    apiMocks.rechercher.mockResolvedValue(page([]));
    apiMocks.listerCategoriesActives.mockResolvedValue([categorie]);
  });

  it('affiche la liste autorisée sans donnée personnelle employé inutile', async () => {
    apiMocks.rechercher.mockResolvedValue(page([employe]));
    application('/employes', ['EMPLOYE_CONSULTER']);
    expect(await screen.findByText('Sara Bennani')).toBeInTheDocument();
    expect(screen.getByText('Serveuse')).toBeInTheDocument();
    expect(screen.queryByText('+212 600 000 000')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Créer un employé' })).not.toBeInTheDocument();
  });

  it('affiche 403 sans permission de consultation et ne lance pas la liste', async () => {
    application('/clients', ['CLIENT_GERER']);
    expect(await screen.findByRole('heading', { name: 'Accès refusé' })).toBeInTheDocument();
    expect(apiMocks.rechercher).not.toHaveBeenCalled();
  });

  it('valide localement un formulaire avant la création', async () => {
    application('/categories', ['CATEGORIE_CONSULTER', 'CATEGORIE_GERER']);
    const user = userEvent.setup();
    await screen.findByRole('heading', { name: 'Catégories' });
    await user.click(screen.getByRole('button', { name: 'Créer une catégorie' }));
    await user.click(screen.getByRole('button', { name: 'Créer une catégorie' }));
    expect(await screen.findByText('Le nom est obligatoire.')).toBeInTheDocument();
    expect(apiMocks.creer).not.toHaveBeenCalled();
  });

  it('crée une table avec le DTO attendu', async () => {
    apiMocks.creer.mockResolvedValue(table);
    application('/tables', ['TABLE_CONSULTER', 'TABLE_GERER']);
    const user = userEvent.setup();
    await screen.findByRole('heading', { name: 'Tables' });
    await user.click(screen.getByRole('button', { name: 'Créer une table' }));
    await user.type(screen.getByLabelText('Numéro'), '12');
    await user.type(screen.getByLabelText('Capacité'), '4');
    await user.click(screen.getByRole('button', { name: 'Créer une table' }));
    await waitFor(() => expect(apiMocks.creer).toHaveBeenCalledWith(expect.objectContaining({ id: 'tables' }), { numero: 12, capacite: 4 }));
  });

  it('crée un produit puis enregistre l’image facultative avec son nouvel identifiant', async () => {
    const produitCree = { ...produit, id: 10, nom: 'Citronnade', imageUrl: null, imageTypeMime: null };
    const produitAvecImage = { ...produitCree, imageUrl: '/api/produits/10/image', imageTypeMime: 'image/png', version: 1 };
    apiMocks.creer.mockResolvedValue(produitCree);
    apiMocks.enregistrerImageProduit.mockResolvedValue(produitAvecImage);
    application('/produits', ['PRODUIT_CONSULTER', 'PRODUIT_GERER', 'CATEGORIE_CONSULTER']);
    const user = userEvent.setup();

    await screen.findByRole('heading', { name: 'Produits' });
    await user.click(screen.getByRole('button', { name: 'Créer un produit' }));
    await user.type(screen.getByLabelText('Nom'), 'Citronnade');
    await user.type(screen.getByLabelText('Prix'), '24');
    await user.selectOptions(screen.getByLabelText('Catégorie'), '2');
    const fichier = new File([new Uint8Array([137, 80, 78, 71])], 'citronnade.png', { type: 'image/png' });
    await user.upload(screen.getByLabelText('Image du produit (facultative)'), fichier);
    await user.click(screen.getByRole('button', { name: 'Créer un produit' }));

    await waitFor(() => expect(apiMocks.creer).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'produits' }),
      expect.objectContaining({ nom: 'Citronnade', prix: 24, categorieId: 2, disponible: true }),
    ));
    await waitFor(() => expect(apiMocks.enregistrerImageProduit).toHaveBeenCalledWith(10, fichier));
    expect(await screen.findByRole('heading', { name: 'Citronnade' })).toBeInTheDocument();
  });

  it('conserve le produit créé et explique comment réessayer si l’envoi de l’image échoue', async () => {
    const produitCree = { ...produit, id: 11, nom: 'Jus d’orange', imageUrl: null, imageTypeMime: null };
    apiMocks.creer.mockResolvedValue(produitCree);
    apiMocks.enregistrerImageProduit.mockRejectedValue(new ApiError(400, 'IMAGE_INVALIDE', 'Image refusée par le serveur.'));
    application('/produits', ['PRODUIT_CONSULTER', 'PRODUIT_GERER', 'CATEGORIE_CONSULTER']);
    const user = userEvent.setup();

    await screen.findByRole('heading', { name: 'Produits' });
    await user.click(screen.getByRole('button', { name: 'Créer un produit' }));
    await user.type(screen.getByLabelText('Nom'), 'Jus d’orange');
    await user.type(screen.getByLabelText('Prix'), '30');
    await user.selectOptions(screen.getByLabelText('Catégorie'), '2');
    await user.upload(
      screen.getByLabelText('Image du produit (facultative)'),
      new File([new Uint8Array([255, 216, 255])], 'orange.jpg', { type: 'image/jpeg' }),
    );
    await user.click(screen.getByRole('button', { name: 'Créer un produit' }));

    expect(await screen.findByText(/Le produit a été créé, mais l’image n’a pas pu être enregistrée/)).toBeInTheDocument();
    expect(screen.getByText(/réessayer depuis « Consulter »/)).toBeInTheDocument();
    expect(apiMocks.creer).toHaveBeenCalledTimes(1);
  });

  it('affiche la situation opérationnelle complète d’une table sans ancienne alerte', async () => {
    apiMocks.rechercher.mockResolvedValue(page([table]));
    application('/tables', ['TABLE_CONSULTER']);

    expect(await screen.findByText('Occupée')).toBeInTheDocument();
    expect(screen.getByText('Indisponible')).toBeInTheDocument();
    expect(screen.getByText('CMD-014')).toBeInTheDocument();
    expect(screen.getByText('RES-018')).toBeInTheDocument();
    expect(screen.queryByText(/L’API actuelle fournit uniquement le numéro/)).not.toBeInTheDocument();
  });

  it('affiche une erreur renvoyée par l’API de liste', async () => {
    apiMocks.rechercher.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR', 'Service temporairement indisponible.'));
    application('/clients', ['CLIENT_CONSULTER']);
    expect(await screen.findByText('Service temporairement indisponible.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Réessayer' })).toBeInTheDocument();
  });

  it('désactive une catégorie uniquement après confirmation', async () => {
    apiMocks.rechercher.mockResolvedValue(page([categorie]));
    apiMocks.obtenir.mockResolvedValue(categorie);
    apiMocks.modifierStatut.mockResolvedValue({ ...categorie, actif: false, version: 1 });
    application('/categories', ['CATEGORIE_CONSULTER', 'CATEGORIE_GERER']);
    const user = userEvent.setup();
    await screen.findByText('Boissons');
    await user.click(screen.getByRole('button', { name: 'Consulter' }));
    await screen.findByRole('heading', { name: 'Boissons' });
    await user.click(screen.getByRole('button', { name: 'Désactiver' }));
    expect(apiMocks.modifierStatut).not.toHaveBeenCalled();
    const dialogue = await screen.findByRole('alertdialog');
    await user.click(within(dialogue).getByRole('button', { name: 'Confirmer la désactivation' }));
    await waitFor(() => expect(apiMocks.modifierStatut).toHaveBeenCalledWith(expect.objectContaining({ id: 'categories' }), 2, false));
  });

  it('demande une confirmation spécifique avant une modification de prix', async () => {
    apiMocks.rechercher.mockResolvedValue(page([produit]));
    apiMocks.obtenir.mockResolvedValue(produit);
    apiMocks.modifier.mockResolvedValue({ ...produit, prix: 20, version: 1 });
    application('/produits', ['PRODUIT_CONSULTER', 'PRODUIT_GERER', 'CATEGORIE_CONSULTER']);
    const user = userEvent.setup();
    await screen.findByText('Thé à la menthe');
    await user.click(screen.getByRole('button', { name: 'Consulter' }));
    await screen.findByRole('heading', { name: 'Thé à la menthe' });
    await user.click(screen.getByRole('button', { name: 'Modifier' }));
    const prix = screen.getByLabelText('Prix');
    await user.clear(prix);
    await user.type(prix, '20');
    await user.click(screen.getByRole('button', { name: 'Enregistrer les modifications' }));
    expect(await screen.findByRole('heading', { name: 'Confirmer la modification du prix ?' })).toBeInTheDocument();
    expect(apiMocks.modifier).not.toHaveBeenCalled();
    await user.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Modifier le prix' }));
    await waitFor(() => expect(apiMocks.modifier).toHaveBeenCalledWith(expect.objectContaining({ id: 'produits' }), 9, expect.objectContaining({ prix: 20, categorieId: 2 })));
  });

  it('affiche la disponibilité et permet de remplacer une image produit', async () => {
    apiMocks.rechercher.mockResolvedValue(page([produit]));
    apiMocks.obtenir.mockResolvedValue(produit);
    apiMocks.enregistrerImageProduit.mockResolvedValue({ ...produit, version: 1 });
    application('/produits', ['PRODUIT_CONSULTER', 'PRODUIT_GERER', 'CATEGORIE_CONSULTER']);
    const user = userEvent.setup();

    expect(await screen.findByText('Disponible')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Consulter' }));
    expect(await screen.findByText('Disponible à la commande')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Présentation de Thé à la menthe' }).getAttribute('src')).toBe('/api/produits/9/image?v=0');
    expect(screen.queryByText(/La disponibilité métier et les images ne sont pas fournies/)).not.toBeInTheDocument();

    const fichier = new File([new Uint8Array([137, 80, 78, 71])], 'the.png', { type: 'image/png' });
    await user.upload(screen.getByLabelText(/PNG, JPEG ou WebP/), fichier);
    await user.click(screen.getByRole('button', { name: 'Remplacer l’image' }));
    await waitFor(() => expect(apiMocks.enregistrerImageProduit).toHaveBeenCalledWith(9, fichier));
  });

  it('transmet la disponibilité métier lors de la modification', async () => {
    apiMocks.rechercher.mockResolvedValue(page([produit]));
    apiMocks.obtenir.mockResolvedValue(produit);
    apiMocks.modifier.mockResolvedValue({ ...produit, disponible: false, version: 1 });
    application('/produits', ['PRODUIT_CONSULTER', 'PRODUIT_GERER', 'CATEGORIE_CONSULTER']);
    const user = userEvent.setup();

    await screen.findByText('Thé à la menthe');
    await user.click(screen.getByRole('button', { name: 'Consulter' }));
    await user.click(await screen.findByRole('button', { name: 'Modifier' }));
    await user.selectOptions(screen.getByLabelText('Disponibilité métier'), 'false');
    await user.click(screen.getByRole('button', { name: 'Enregistrer les modifications' }));

    await waitFor(() => expect(apiMocks.modifier).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'produits' }), 9, expect.objectContaining({ disponible: false }),
    ));
  });

  it('réinitialise les données lors de la navigation de tables vers produits', async () => {
    apiMocks.rechercher.mockImplementation((configuration: { id: string }) =>
      Promise.resolve(configuration.id === 'tables' ? page([table]) : page([produit])),
    );
    application('/tables', [
      'TABLE_CONSULTER',
      'PRODUIT_CONSULTER',
      'CATEGORIE_CONSULTER',
    ]);
    const user = userEvent.setup();

    expect(await screen.findByText('Table 12')).toBeInTheDocument();
    await user.click(screen.getByRole('link', { name: 'Produits' }));

    expect(await screen.findByRole('heading', { name: 'Produits' })).toBeInTheDocument();
    expect(await screen.findByText('Thé à la menthe')).toBeInTheDocument();
  });
});
