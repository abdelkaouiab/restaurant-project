import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../../auth/AuthContext';
import { AppRoutes } from '../../routes/AppRoutes';
import { RouterProvider } from '../../routes/Router';
import { render, screen, userEvent, waitFor } from '../../test/dom';
import type { UtilisateurAuthentifie } from '../../types/auth';
import type { TableauBordOperations } from './types';

const authMocks = vi.hoisted(() => ({ me: vi.fn(), login: vi.fn(), logout: vi.fn() }));
const apiMocks = vi.hoisted(() => ({ operations: vi.fn(), finances: vi.fn(), journal: vi.fn(), utilisateursJournal: vi.fn(), compterUtilisateurs: vi.fn(), commandesAPayer: vi.fn(), paiementsDuJour: vi.fn(), facturesRecentes: vi.fn(), commandesCuisine: vi.fn(), compterAchats: vi.fn() }));
vi.mock('../../api/authApi', () => ({ authApi: { utilisateurCourant: authMocks.me, connexion: authMocks.login, deconnexion: authMocks.logout } }));
vi.mock('./dashboardApi', () => ({ dashboardApi: apiMocks }));

const operations: TableauBordOperations = {
  debut: '2026-08-03', fin: '2026-08-03', nombreCommandes: 12, nombreTablesDisponibles: 6,
  commandesParEtat: [{ libelle: 'BROUILLON', valeur: 1 }, { libelle: 'CONFIRMEE', valeur: 3 }, { libelle: 'EN_PREPARATION', valeur: 4 }, { libelle: 'PRETE', valeur: 2 }, { libelle: 'PAYEE', valeur: 2 }],
  produitsLesPlusVendus: [{ produitId: 4, produit: 'Tajine', quantite: 9 }],
  reservations: [{ libelle: 'EN_ATTENTE', valeur: 2 }, { libelle: 'CONFIRMEE', valeur: 3 }],
  stocksFaibles: [{ ingredientId: 7, ingredient: 'Farine', unite: 'KILOGRAMME', quantiteDisponible: 1, seuilAlerte: 2 }],
};
const finances = { debut: '2026-08-03', fin: '2026-08-03', chiffreAffaires: 1250, paiementsParMode: [{ libelle: 'CARTE', valeur: 800 }, { libelle: 'ESPECES', valeur: 450 }], depensesAchat: 320 };
const evenement = { id: 51, utilisateurId: null, utilisateurNom: null, action: 'CONNEXION', typeRessource: 'AUTHENTIFICATION', ressourceId: null, resultat: 'ECHEC' as const, correlationId: 'f13ef765-376d-482b-8b14-eb8a6c5de970', creeLe: '2026-08-03T10:00:00Z' };

function utilisateur(role: string, permissions: string[]): UtilisateurAuthentifie {
  return { id: 1, email: `${role.toLowerCase()}@restaurant.test`, employe: { id: 1, matricule: 'EMP-1', nom: 'Amrani', prenom: 'Sara', poste: role }, roles: [role], permissions };
}
function application(role: string, permissions: string[], route = '/tableau-de-bord') {
  authMocks.me.mockResolvedValue(utilisateur(role, permissions));
  return render(<RouterProvider initialPath={route}><AuthProvider><AppRoutes /></AuthProvider></RouterProvider>);
}

describe('tableaux de bord, rapports et journal', () => {
  beforeEach(() => {
    Object.values(authMocks).forEach((mock) => mock.mockReset());
    Object.values(apiMocks).forEach((mock) => mock.mockReset());
    apiMocks.operations.mockResolvedValue(operations);
    apiMocks.finances.mockResolvedValue(finances);
    apiMocks.journal.mockResolvedValue({ contenu: [evenement], page: 0, taille: 20, totalElements: 1, totalPages: 1 });
    apiMocks.utilisateursJournal.mockResolvedValue([{ id: 7, email: 'sara@restaurant.test', actif: true, nom: 'Amrani', prenom: 'Sara' }]);
    apiMocks.compterUtilisateurs.mockImplementation((actif: boolean) => Promise.resolve(actif ? 18 : 3));
    apiMocks.commandesAPayer.mockResolvedValue([{ numeroCommande: 'CMD-PAY-1', table: 5, statutCommande: 'PRETE', montantTotal: 50, montantPaye: 10, montantRestant: 40, heure: '2026-08-03T10:00:00Z' }]);
    apiMocks.paiementsDuJour.mockResolvedValue({ contenu: [{ id: 9, numeroCommande: 'CMD-PAID-1', montant: 50, mode: 'CARTE', statut: 'VALIDE', creeLe: '2026-08-03T11:00:00Z' }], page: 0, taille: 5, totalElements: 1, totalPages: 1 });
    apiMocks.facturesRecentes.mockResolvedValue({ contenu: [{ numero: 'FAC-1', numeroCommande: 'CMD-PAID-1', montantTotal: 50, emiseLe: '2026-08-03T11:00:00Z' }], page: 0, taille: 5, totalElements: 1, totalPages: 1 });
    apiMocks.commandesCuisine.mockResolvedValue([{ numero: 'CMD-CUI-1', table: 4, produits: [], heure: '2026-08-03T10:00:00Z', etat: 'CONFIRMEE' }, { numero: 'CMD-CUI-2', table: 6, produits: [], heure: '2026-08-03T10:05:00Z', etat: 'EN_PREPARATION' }, { numero: 'CMD-CUI-3', table: 8, produits: [], heure: '2026-08-03T10:10:00Z', etat: 'PRETE' }]);
    apiMocks.compterAchats.mockImplementation((statut: string) => Promise.resolve(statut === 'COMMANDE' ? 4 : 2));
  });

  it('affiche à l’administrateur les comptes et les événements récents de sécurité', async () => {
    application('ADMINISTRATEUR', ['TABLEAU_BORD_CONSULTER', 'UTILISATEUR_CONSULTER', 'JOURNAL_ACTIVITE_CONSULTER']);
    expect(await screen.findByText('Utilisateurs actifs')).toBeInTheDocument();
    expect(screen.getByText('18')).toBeInTheDocument();
    expect(screen.getByText('Comptes désactivés')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByText('Connexion')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ouvrir le journal' })).toBeInTheDocument();
    expect(apiMocks.finances).not.toHaveBeenCalled();
  });

  it('affiche au gérant les indicateurs opérationnels et financiers', async () => {
    application('GERANT', ['TABLEAU_BORD_CONSULTER', 'TABLEAU_BORD_FINANCIER_CONSULTER']);
    expect(await screen.findByText('Chiffre d’affaires')).toBeInTheDocument();
    expect(screen.getByText(/1.*250,00/)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Commandes par état' })).toBeInTheDocument();
    expect(screen.getByText('Tajine')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Paiements par mode' })).toBeInTheDocument();
  });

  it('limite le serveur aux commandes et réservations du jour', async () => {
    application('SERVEUR', ['TABLEAU_BORD_CONSULTER', 'TABLE_CONSULTER', 'RESERVATION_GERER', 'COMMANDE_CONSULTER']);
    expect(await screen.findByText('Commandes en préparation')).toBeInTheDocument();
    expect(screen.getByText('Tables disponibles')).toBeInTheDocument();
    expect(screen.getByText('Commandes prêtes')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Réservations du jour' })).toBeInTheDocument();
    expect(screen.queryByText('Chiffre d’affaires')).not.toBeInTheDocument();
    expect(apiMocks.finances).not.toHaveBeenCalled();
    expect(screen.queryByText(/disponibilité réelle des tables n’est pas exposée/i)).not.toBeInTheDocument();
  });

  it('affiche au caissier les commandes à payer, paiements du jour et factures récentes', async () => {
    application('CAISSIER', ['TABLEAU_BORD_CONSULTER', 'CAISSE_PAIEMENTS_GERER']);
    expect(await screen.findByText('Commandes à payer')).toBeInTheDocument();
    expect(screen.getByText('CMD-PAY-1 · Table 5')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Paiements du jour' })).toBeInTheDocument();
    expect(screen.getByText('CMD-PAID-1 · Carte')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Factures récentes' })).toBeInTheDocument();
    expect(screen.getByText('FAC-1 · CMD-PAID-1')).toBeInTheDocument();
    expect(screen.queryByText(/ne disposent pas de routes de consultation/i)).not.toBeInTheDocument();
    expect(apiMocks.paiementsDuJour).toHaveBeenCalledWith(expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/), expect.any(AbortSignal));
    expect(apiMocks.facturesRecentes).toHaveBeenCalledWith(expect.any(AbortSignal));
    expect(apiMocks.finances).not.toHaveBeenCalled();
  });

  it('affiche à la cuisine les commandes actives et prêtes récemment', async () => {
    application('CUISINE', ['TABLEAU_BORD_CONSULTER', 'CUISINE_COMMANDES_GERER']);
    expect(await screen.findByText('Commandes en attente')).toBeInTheDocument();
    expect(screen.getByText('Commandes en préparation')).toBeInTheDocument();
    expect(screen.getByText('Commandes prêtes récemment')).toBeInTheDocument();
    expect(apiMocks.commandesCuisine).toHaveBeenCalledTimes(1);
  });

  it('affiche au responsable stock les alertes et états d’achats retournés', async () => {
    application('RESPONSABLE_STOCK', ['TABLEAU_BORD_CONSULTER', 'STOCK_CONSULTER', 'ACHAT_CONSULTER']);
    expect(await screen.findByText('Stocks faibles')).toBeInTheDocument();
    expect(screen.getByText('Achats en attente')).toBeInTheDocument();
    expect(screen.getByText('Réceptions à terminer')).toBeInTheDocument();
    expect(screen.getByText('Farine')).toBeInTheDocument();
    expect(apiMocks.compterAchats).toHaveBeenCalledWith('COMMANDE', expect.any(AbortSignal));
  });

  it('refuse le journal sans permission et ne lance aucun appel', async () => {
    application('SERVEUR', ['TABLEAU_BORD_CONSULTER'], '/journal-activite');
    expect(await screen.findByRole('heading', { name: 'Accès refusé' })).toBeInTheDocument();
    expect(apiMocks.journal).not.toHaveBeenCalled();
  });

  it('filtre le journal autorisé et n’affiche aucun champ sensible supplémentaire', async () => {
    apiMocks.journal.mockResolvedValue({ contenu: [{ ...evenement, details: 'password=secret', adresseIp: '127.0.0.1' }], page: 0, taille: 20, totalElements: 1, totalPages: 1 });
    application('ADMINISTRATEUR', ['TABLEAU_BORD_CONSULTER', 'JOURNAL_ACTIVITE_CONSULTER'], '/journal-activite');
    const user = userEvent.setup();
    await screen.findByRole('heading', { name: 'Journal d’activité' });
    expect(screen.queryByText('password=secret')).not.toBeInTheDocument();
    expect(screen.queryByText('127.0.0.1')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Modifier|Supprimer/ })).not.toBeInTheDocument();
    const actionSelect = screen.getByLabelText(/^Action/) as HTMLSelectElement;
    const ressourceSelect = screen.getByLabelText(/^Ressource/) as HTMLSelectElement;
    const utilisateurSelect = screen.getByLabelText(/^Utilisateur/) as HTMLSelectElement;
    expect(Array.from(actionSelect.options).map((option) => option.textContent)).toContain('Connexion');
    expect(Array.from(ressourceSelect.options).map((option) => option.textContent)).toContain('Authentification');
    await waitFor(() => expect(utilisateurSelect.disabled).toBe(false));
    expect(Array.from(utilisateurSelect.options).map((option) => option.textContent)).toContain('Sara Amrani — sara@restaurant.test');
    await user.selectOptions(actionSelect, 'CONNEXION');
    await user.selectOptions(ressourceSelect, 'AUTHENTIFICATION');
    await user.selectOptions(screen.getByLabelText(/^Résultat/), 'ECHEC');
    await user.selectOptions(utilisateurSelect, '7');
    await user.click(screen.getByRole('button', { name: 'Rechercher' }));
    await waitFor(() => expect(apiMocks.journal).toHaveBeenCalledTimes(2));
    expect(apiMocks.journal.mock.calls[1][0]).toMatchObject({ action: 'CONNEXION', typeRessource: 'AUTHENTIFICATION', resultat: 'ECHEC', utilisateurId: 7, page: 0, taille: 20 });
  });
});
