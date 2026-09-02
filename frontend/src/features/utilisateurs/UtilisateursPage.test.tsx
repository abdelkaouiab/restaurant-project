import { render, screen, userEvent, waitFor, within } from '../../test/dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../../auth/AuthContext';
import { AppRoutes } from '../../routes/AppRoutes';
import { RouterProvider } from '../../routes/Router';
import type { UtilisateurAuthentifie } from '../../types/auth';
import type { UtilisateurAdministration } from './types';

const mocks = vi.hoisted(() => ({
  me: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  rechercher: vi.fn(),
  creer: vi.fn(),
  modifier: vi.fn(),
  modifierRoles: vi.fn(),
  modifierStatut: vi.fn(),
  reinitialiserMotDePasse: vi.fn(),
  listerEmployesActifs: vi.fn(),
  compterAdministrateursActifs: vi.fn(),
}));

vi.mock('../../api/authApi', () => ({
  authApi: { utilisateurCourant: mocks.me, connexion: mocks.login, deconnexion: mocks.logout },
}));

vi.mock('./utilisateurApi', () => ({
  utilisateurApi: {
    rechercher: mocks.rechercher,
    creer: mocks.creer,
    modifier: mocks.modifier,
    modifierRoles: mocks.modifierRoles,
    modifierStatut: mocks.modifierStatut,
    reinitialiserMotDePasse: mocks.reinitialiserMotDePasse,
    listerEmployesActifs: mocks.listerEmployesActifs,
    compterAdministrateursActifs: mocks.compterAdministrateursActifs,
  },
}));

const administrateur: UtilisateurAuthentifie = {
  id: 99,
  email: 'admin@restaurant.test',
  employe: { id: 99, matricule: 'EMP-099', nom: 'Admin', prenom: 'Amina', poste: 'Administration' },
  roles: ['ADMINISTRATEUR'],
  permissions: ['UTILISATEUR_CONSULTER', 'UTILISATEUR_GERER', 'ROLE_CONSULTER', 'ROLE_GERER', 'EMPLOYE_CONSULTER'],
};

const utilisateurGere: UtilisateurAdministration = {
  id: 7,
  email: 'serveur@restaurant.test',
  actif: true,
  employe: { id: 12, matricule: 'EMP-012', nom: 'El Idrissi', prenom: 'Nora' },
  roles: ['SERVEUR'],
  creeLe: '2026-01-10T10:00:00Z',
  modifieLe: '2026-01-10T10:00:00Z',
  version: 0,
};

const employeDisponible = {
  ...utilisateurGere.employe,
  telephone: null,
  poste: 'Serveuse',
  dateEmbauche: '2025-01-02',
  actif: true,
  creeLe: '2025-01-02T10:00:00Z',
  modifieLe: '2025-01-02T10:00:00Z',
  version: 0,
};

function page(utilisateurs: UtilisateurAdministration[] = [utilisateurGere]) {
  return { contenu: utilisateurs, page: 0, taille: 10, totalElements: utilisateurs.length, totalPages: utilisateurs.length ? 1 : 0 };
}

function application() {
  return render(<RouterProvider initialPath="/utilisateurs"><AuthProvider><AppRoutes /></AuthProvider></RouterProvider>);
}

async function ouvrirDetail() {
  application();
  await screen.findByRole('heading', { name: 'Liste des utilisateurs' });
  await screen.findByText('serveur@restaurant.test');
  await userEvent.setup().click(screen.getByRole('button', { name: 'Consulter' }));
  return screen.findByRole('heading', { name: 'Nora El Idrissi' });
}

describe('administration des utilisateurs', () => {
  beforeEach(() => {
    Object.values(mocks).forEach((mock) => mock.mockReset());
    mocks.me.mockResolvedValue(administrateur);
    mocks.rechercher.mockResolvedValue(page());
    mocks.listerEmployesActifs.mockResolvedValue([employeDisponible]);
    mocks.compterAdministrateursActifs.mockResolvedValue(2);
  });

  it('autorise un administrateur et charge la liste depuis le backend', async () => {
    application();
    expect(await screen.findByRole('heading', { name: 'Utilisateurs' })).toBeInTheDocument();
    expect(await screen.findByText('serveur@restaurant.test')).toBeInTheDocument();
    expect(mocks.rechercher).toHaveBeenCalledWith(expect.objectContaining({ page: 0, taille: 10, tri: 'email' }), expect.any(AbortSignal));
  });

  it('affiche 403 lorsque la permission de consultation manque', async () => {
    mocks.me.mockResolvedValue({ ...administrateur, permissions: [] });
    application();
    expect(await screen.findByRole('heading', { name: 'Accès refusé' })).toBeInTheDocument();
    expect(mocks.rechercher).not.toHaveBeenCalled();
  });

  it('crée un utilisateur après validation et confirmation', async () => {
    const cree = { ...utilisateurGere, id: 8, email: 'nouveau@restaurant.test' };
    mocks.creer.mockResolvedValue(cree);
    application();
    const user = userEvent.setup();
    await screen.findByText('serveur@restaurant.test');
    await user.click(screen.getByRole('button', { name: 'Créer un utilisateur' }));
    await screen.findByText('Nora El Idrissi — EMP-012');
    await user.type(screen.getByLabelText('Adresse e-mail', { selector: '#nouvel-email' }), 'nouveau@restaurant.test');
    await user.selectOptions(screen.getByLabelText('Employé'), '12');
    await user.click(screen.getByLabelText('Serveur'));
    await user.type(screen.getByLabelText('Mot de passe initial'), 'Mot-de-passe-2026!');
    await user.type(screen.getByLabelText('Confirmer le mot de passe'), 'Mot-de-passe-2026!');
    await user.click(screen.getByRole('button', { name: 'Créer le compte' }));
    await user.click(await screen.findByRole('button', { name: 'Confirmer la création' }));

    await waitFor(() => expect(mocks.creer).toHaveBeenCalledWith({
      email: 'nouveau@restaurant.test', motDePasse: 'Mot-de-passe-2026!', employeId: 12, roles: ['SERVEUR'],
    }));
    expect(screen.queryByDisplayValue('Mot-de-passe-2026!')).not.toBeInTheDocument();
  });

  it('bloque côté client un formulaire de création invalide', async () => {
    application();
    const user = userEvent.setup();
    await screen.findByText('serveur@restaurant.test');
    await user.click(screen.getByRole('button', { name: 'Créer un utilisateur' }));
    await screen.findByText('Nora El Idrissi — EMP-012');
    await user.type(screen.getByLabelText('Adresse e-mail', { selector: '#nouvel-email' }), 'adresse-invalide');
    await user.type(screen.getByLabelText('Mot de passe initial'), 'court');
    await user.click(screen.getByRole('button', { name: 'Créer le compte' }));
    expect(await screen.findByText(/Saisissez une adresse e-mail valide/)).toBeInTheDocument();
    expect(screen.getByText(/doit contenir entre 12 et 128 caractères/)).toBeInTheDocument();
    expect(mocks.creer).not.toHaveBeenCalled();
  });

  it('désactive un compte après confirmation', async () => {
    mocks.modifierStatut.mockResolvedValue({ ...utilisateurGere, actif: false });
    await ouvrirDetail();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Désactiver le compte' }));
    const dialogue = await screen.findByRole('alertdialog');
    await user.click(within(dialogue).getByRole('button', { name: 'Confirmer' }));
    await waitFor(() => expect(mocks.modifierStatut).toHaveBeenCalledWith(7, false));
  });

  it('attribue un rôle après confirmation', async () => {
    mocks.modifierRoles.mockResolvedValue({ ...utilisateurGere, roles: ['SERVEUR', 'CAISSIER'] });
    await ouvrirDetail();
    const user = userEvent.setup();
    await user.click(screen.getByLabelText('Caissier'));
    await user.click(screen.getByRole('button', { name: 'Enregistrer les rôles' }));
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Confirmer' }));
    await waitFor(() => expect(mocks.modifierRoles).toHaveBeenCalledWith(7, ['SERVEUR', 'CAISSIER']));
  });

  it('n’affiche aucun hash éventuellement ajouté à une réponse serveur', async () => {
    mocks.rechercher.mockResolvedValue(page([{ ...utilisateurGere, motDePasseHash: '$2a$10$hash-interdit' } as UtilisateurAdministration]));
    await ouvrirDetail();
    expect(screen.queryByText(/hash-interdit/)).not.toBeInTheDocument();
    expect(screen.queryByText(/motDePasseHash/)).not.toBeInTheDocument();
  });
});
