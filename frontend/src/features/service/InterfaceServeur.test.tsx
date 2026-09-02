import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api/client';
import { AuthProvider } from '../../auth/AuthContext';
import { AppRoutes } from '../../routes/AppRoutes';
import { RouterProvider } from '../../routes/Router';
import { render, screen, userEvent, waitFor, within } from '../../test/dom';
import type { UtilisateurAuthentifie } from '../../types/auth';
import type { Categorie, Client, Produit, TableRestaurantSimple } from '../referentiels/types';
import type { Commande, Reservation } from './types';

const authMocks = vi.hoisted(() => ({ me: vi.fn(), login: vi.fn(), logout: vi.fn() }));
const dashboardMocks = vi.hoisted(() => ({ operations: vi.fn(), finances: vi.fn(), journal: vi.fn(), compterUtilisateurs: vi.fn(), commandesAPayer: vi.fn(), commandesCuisine: vi.fn(), compterAchats: vi.fn() }));
const apiMocks = vi.hoisted(() => ({
  listerCommandes: vi.fn(), obtenirCommande: vi.fn(), creerCommande: vi.fn(),
  modifierAssociation: vi.fn(), ajouterLigne: vi.fn(), modifierLigne: vi.fn(),
  supprimerLigne: vi.fn(), confirmerCommande: vi.fn(), annulerCommande: vi.fn(),
  listerReservations: vi.fn(), creerReservation: vi.fn(), transitionReservation: vi.fn(),
  creerCommandeReservation: vi.fn(), listerTablesActives: vi.fn(),
  listerClientsActifs: vi.fn(), listerProduitsActifs: vi.fn(),
}));

vi.mock('../../api/authApi', () => ({ authApi: { utilisateurCourant: authMocks.me, connexion: authMocks.login, deconnexion: authMocks.logout } }));
vi.mock('../dashboard/dashboardApi', () => ({ dashboardApi: dashboardMocks }));
vi.mock('./serviceApi', () => ({ serviceApi: apiMocks }));

const table: TableRestaurantSimple = {
  id: 1, numero: 7, capacite: 4, actif: true,
  creeLe: '2026-01-01T10:00:00Z', modifieLe: '2026-01-01T10:00:00Z', version: 0,
};
const client: Client = { id: 2, nom: 'Amrani', prenom: 'Leïla', email: null, telephone: null, actif: true, creeLe: '2026-01-01T10:00:00Z', modifieLe: '2026-01-01T10:00:00Z', version: 0 };
const categorie: Categorie = { id: 3, nom: 'Plats', description: null, actif: true, creeLe: '2026-01-01T10:00:00Z', modifieLe: '2026-01-01T10:00:00Z', version: 0 };
const produit: Produit = { id: 4, nom: 'Tajine', description: null, prix: 65, categorie, disponible: true, imageUrl: null, imageTypeMime: null, actif: true, creeLe: '2026-01-01T10:00:00Z', modifieLe: '2026-01-01T10:00:00Z', version: 0 };

const brouillon: Commande = {
  id: 10, numero: 'CMD-20260803-ABC', statut: 'BROUILLON', table, client,
  creeParId: 5, creeParEmail: 'serveur@restaurant.test', lignes: [], montantTotal: 0,
  confirmeeLe: null, annuleeLe: null, creeLe: '2026-08-03T10:00:00Z', modifieLe: '2026-08-03T10:00:00Z', version: 0,
};

const avecLigne: Commande = {
  ...brouillon, lignes: [{ id: 20, produitId: 4, produitNom: 'Tajine', quantite: 1, prixUnitaire: 65, sousTotal: 65, version: 0 }], montantTotal: 65,
};

const reservation: Reservation = {
  id: 30, numero: 'RES-20260803-ABC', tableId: 1, numeroTable: 7, clientId: 2,
  nombrePersonnes: 2, debut: '2030-01-01T18:00:00Z', fin: '2030-01-01T20:00:00Z',
  statut: 'EN_ATTENTE', remarques: null, numeroCommande: null, version: 0,
};

function page<T>(contenu: T[]) {
  return { contenu, page: 0, taille: 10, totalElements: contenu.length, totalPages: contenu.length ? 1 : 0 };
}

function serveur(permissions = ['TABLEAU_BORD_CONSULTER', 'TABLE_CONSULTER', 'CLIENT_CONSULTER', 'CLIENT_GERER', 'PRODUIT_CONSULTER', 'COMMANDE_CONSULTER', 'COMMANDE_GERER', 'RESERVATION_GERER']): UtilisateurAuthentifie {
  return { id: 5, email: 'serveur@restaurant.test', employe: { id: 5, matricule: 'SRV-5', nom: 'Idrissi', prenom: 'Nora', poste: 'Serveuse' }, roles: ['SERVEUR'], permissions };
}

function application(route: string, permissions?: string[]) {
  authMocks.me.mockResolvedValue(serveur(permissions));
  return render(<RouterProvider initialPath={route}><AuthProvider><AppRoutes /></AuthProvider></RouterProvider>);
}

async function ouvrirCommande(commande: Commande) {
  apiMocks.listerCommandes.mockResolvedValue(page([commande]));
  apiMocks.obtenirCommande.mockResolvedValue(commande);
  application('/commandes');
  const user = userEvent.setup();
  await screen.findByText(commande.numero);
  await user.click(screen.getByRole('button', { name: 'Consulter' }));
  await screen.findByRole('heading', { name: commande.numero });
  return user;
}

describe('interface du serveur', () => {
  beforeEach(() => {
    Object.values(authMocks).forEach((mock) => mock.mockReset());
    Object.values(apiMocks).forEach((mock) => mock.mockReset());
    Object.values(dashboardMocks).forEach((mock) => mock.mockReset());
    dashboardMocks.operations.mockResolvedValue({ debut: '2026-08-03', fin: '2026-08-03', nombreCommandes: 0, nombreTablesDisponibles: 2, commandesParEtat: [], produitsLesPlusVendus: [], reservations: [], stocksFaibles: [] });
    apiMocks.listerCommandes.mockResolvedValue(page([]));
    apiMocks.listerReservations.mockResolvedValue(page([]));
    apiMocks.listerTablesActives.mockResolvedValue([table]);
    apiMocks.listerClientsActifs.mockResolvedValue([client]);
    apiMocks.listerProduitsActifs.mockResolvedValue([produit]);
  });

  it('affiche les quatre raccourcis du tableau de bord SERVEUR', async () => {
    application('/tableau-de-bord');
    await screen.findByRole('heading', { name: 'Bonjour Nora' });
    expect(screen.getByText('Tables disponibles')).toBeInTheDocument();
    const raccourcis = within(screen.getByRole('navigation', { name: 'Navigation' }));
    expect(raccourcis.getByRole('link', { name: 'Tables' })).toBeInTheDocument();
    expect(raccourcis.getByRole('link', { name: 'Clients' })).toBeInTheDocument();
    expect(raccourcis.getByRole('link', { name: 'Réservations' })).toBeInTheDocument();
    expect(raccourcis.getByRole('link', { name: 'Commandes' })).toBeInTheDocument();
  });

  it('affiche le conflit renvoyé pour une commande vide', async () => {
    apiMocks.confirmerCommande.mockRejectedValue(new ApiError(409, 'BUSINESS_CONFLICT', 'Une commande doit contenir au moins une ligne.'));
    const user = await ouvrirCommande(brouillon);
    await user.click(screen.getByRole('button', { name: 'Confirmer la commande' }));
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Confirmer la commande' }));
    expect(await screen.findByText('Cette commande vient d’être modifiée : Une commande doit contenir au moins une ligne.')).toBeInTheDocument();
  });

  it('affiche un produit devenu indisponible sans envoyer son prix', async () => {
    apiMocks.ajouterLigne.mockRejectedValue(new ApiError(409, 'BUSINESS_CONFLICT', 'Le produit n’est pas disponible.'));
    const user = await ouvrirCommande(brouillon);
    await user.selectOptions(screen.getByLabelText('Produit'), '4');
    await user.type(screen.getByLabelText('Remarque (facultative)'), 'Sans sel');
    await user.click(screen.getByRole('button', { name: 'Ajouter à la commande' }));
    expect(await screen.findByText('Cette commande vient d’être modifiée : Le produit n’est pas disponible.')).toBeInTheDocument();
    expect(apiMocks.ajouterLigne).toHaveBeenCalledWith(10, { produitId: 4, quantite: 1, remarque: 'Sans sel' });
    expect(apiMocks.ajouterLigne.mock.calls[0][1]).not.toHaveProperty('prix');
    expect(apiMocks.ajouterLigne.mock.calls[0][1]).not.toHaveProperty('sousTotal');
    expect(apiMocks.ajouterLigne.mock.calls[0][1]).not.toHaveProperty('total');
  });

  it('modifie une quantité et conserve les montants renvoyés par le backend', async () => {
    const modifie = { ...avecLigne, lignes: [{ ...avecLigne.lignes[0], quantite: 3, sousTotal: 195, version: 1 }], montantTotal: 195, version: 1 };
    apiMocks.modifierLigne.mockResolvedValue(modifie);
    const user = await ouvrirCommande(avecLigne);
    const quantite = screen.getByLabelText('Quantité de Tajine');
    await user.clear(quantite);
    await user.type(quantite, '3');
    await user.click(screen.getByRole('button', { name: 'Modifier' }));
    await waitFor(() => expect(apiMocks.modifierLigne).toHaveBeenCalledWith(10, 20, { quantite: 3 }));
    expect(await screen.findByText('195,00')).toBeInTheDocument();
  });

  it('confirme une commande et désactive les modifications de lignes', async () => {
    apiMocks.confirmerCommande.mockResolvedValue({ ...avecLigne, statut: 'CONFIRMEE', confirmeeLe: '2026-08-03T10:10:00Z', version: 1 });
    const user = await ouvrirCommande(avecLigne);
    await user.click(screen.getByRole('button', { name: 'Confirmer la commande' }));
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Confirmer la commande' }));
    expect(await screen.findByText('Cette commande n’est plus un brouillon. Les associations et les lignes sont en lecture seule.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Ajouter à la commande' })).not.toBeInTheDocument();
  });

  it('masque les transitions interdites d’une réservation terminée', async () => {
    apiMocks.listerReservations.mockResolvedValue(page([{ ...reservation, statut: 'TERMINEE' }]));
    application('/reservations');
    await screen.findByText('RES-20260803-ABC');
    expect(screen.queryByRole('button', { name: 'Confirmer' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Enregistrer l’arrivée' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Terminer' })).not.toBeInTheDocument();
  });

  it('filtre les réservations par date et statut côté serveur', async () => {
    application('/reservations');
    const user = userEvent.setup();
    await screen.findByRole('heading', { name: 'Réservations' });
    await user.type(screen.getByLabelText('Date'), '2030-01-01');
    await user.selectOptions(screen.getByLabelText('Statut'), 'CONFIRMEE');

    await waitFor(() => expect(apiMocks.listerReservations).toHaveBeenLastCalledWith({
      date: '2030-01-01', statut: 'CONFIRMEE', page: 0, taille: 10,
    }, expect.any(AbortSignal)));
    expect(screen.queryByText(/Les filtres par date et statut ne sont pas disponibles/)).not.toBeInTheDocument();
  });

  it('affiche une transition de réservation refusée par le backend', async () => {
    apiMocks.listerReservations.mockResolvedValue(page([{ ...reservation, statut: 'CONFIRMEE' }]));
    apiMocks.transitionReservation.mockRejectedValue(new ApiError(409, 'BUSINESS_CONFLICT', 'Transition de réservation interdite.'));
    application('/reservations');
    const user = userEvent.setup();
    await screen.findByText('RES-20260803-ABC');
    await user.click(screen.getByRole('button', { name: 'Enregistrer l’arrivée' }));
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Enregistrer l’arrivée' }));
    expect(await screen.findByText('Cette réservation vient d’être modifiée : Transition de réservation interdite.')).toBeInTheDocument();
  });

  it('affiche un conflit de réservation renvoyé par le backend', async () => {
    apiMocks.creerReservation.mockRejectedValue(new ApiError(409, 'BUSINESS_CONFLICT', 'Ce créneau est déjà réservé pour cette table.'));
    application('/reservations');
    const user = userEvent.setup();
    await screen.findByRole('heading', { name: 'Réservations' });
    await user.click(screen.getByRole('button', { name: 'Créer une réservation' }));
    await user.selectOptions(screen.getByLabelText('Table'), '1');
    await user.selectOptions(screen.getByLabelText('Client'), '2');
    await user.type(screen.getByLabelText('Nombre de personnes'), '2');
    await user.type(screen.getByLabelText('Date et heure de début'), '2030-01-01T18:00');
    await user.click(screen.getByRole('button', { name: 'Créer la réservation' }));
    expect(await screen.findByText('Cette réservation vient d’être modifiée : Ce créneau est déjà réservé pour cette table.')).toBeInTheDocument();
  });

  it('affiche 403 et ne consulte aucune commande sans permission', async () => {
    application('/commandes', ['COMMANDE_GERER']);
    expect(await screen.findByRole('heading', { name: 'Accès refusé' })).toBeInTheDocument();
    expect(apiMocks.listerCommandes).not.toHaveBeenCalled();
  });
});
