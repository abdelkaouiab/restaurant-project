import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api/client';
import { AuthProvider } from '../../auth/AuthContext';
import { AppRoutes } from '../../routes/AppRoutes';
import { RouterProvider } from '../../routes/Router';
import { act, render, screen, userEvent, waitFor } from '../../test/dom';
import type { UtilisateurAuthentifie } from '../../types/auth';
import type { CommandeAPayer, Facture, PaiementHistorique, PaiementResponse } from './types';

const authMocks = vi.hoisted(() => ({ me: vi.fn(), login: vi.fn(), logout: vi.fn() }));
const caisseMocks = vi.hoisted(() => ({
  listerCommandesAPayer: vi.fn(),
  enregistrerPaiement: vi.fn(),
  consulterFacture: vi.fn(),
  listerPaiementsCommande: vi.fn(),
  listerFactures: vi.fn(),
  telechargerFacturePdf: vi.fn(),
}));

vi.mock('../../api/authApi', () => ({ authApi: { utilisateurCourant: authMocks.me, connexion: authMocks.login, deconnexion: authMocks.logout } }));
vi.mock('./caisseApi', () => ({ caisseApi: caisseMocks }));

const commande: CommandeAPayer = {
  numeroCommande: 'CMD-CASH-001',
  table: 12,
  statutCommande: 'PRETE',
  montantTotal: 100,
  montantPaye: 20,
  montantRestant: 80,
  heure: '2026-08-03T18:00:00Z',
};

const paiementComplet: PaiementResponse = {
  id: 41,
  cleIdempotence: '912fc4ec-2db3-4f24-8a51-bd4fa477df71',
  numeroCommande: commande.numeroCommande,
  montant: 80,
  mode: 'CARTE',
  statut: 'VALIDE',
  creeLe: '2026-08-03T18:15:00Z',
  montantPaye: 100,
  montantRestant: 0,
  statutCommande: 'PAYEE',
  facture: {
    numero: 'FAC-2026-000041',
    numeroCommande: commande.numeroCommande,
    montantTotal: 100,
    emiseLe: '2026-08-03T18:15:00Z',
  },
};

const facture: Facture = paiementComplet.facture!;
const paiementHistorique: PaiementHistorique = {
  id: 41,
  numeroCommande: commande.numeroCommande,
  montant: 20,
  mode: 'ESPECES',
  statut: 'VALIDE',
  creeLe: '2026-08-03T18:10:00Z',
};

const pageVide = {
  contenu: [],
  page: 0,
  taille: 10,
  totalElements: 0,
  totalPages: 0,
};

function utilisateurCaissier(): UtilisateurAuthentifie {
  return {
    id: 12,
    email: 'caisse@restaurant.test',
    employe: { id: 12, matricule: 'CAI-12', nom: 'Bennani', prenom: 'Amine', poste: 'Caissier' },
    roles: ['CAISSIER'],
    permissions: ['TABLEAU_BORD_CONSULTER', 'CAISSE_PAIEMENTS_GERER'],
  };
}

function application(utilisateur = utilisateurCaissier()) {
  authMocks.me.mockResolvedValue(utilisateur);
  return render(<RouterProvider initialPath="/caisse"><AuthProvider><AppRoutes /></AuthProvider></RouterProvider>);
}

async function ouvrirCommande() {
  const user = userEvent.setup();
  await screen.findByText('CMD-CASH-001');
  await user.click(screen.getByRole('button', { name: /CMD-CASH-001/ }));
  return user;
}

describe('interface du caissier', () => {
  beforeEach(() => {
    Object.values(authMocks).forEach((mock) => mock.mockReset());
    Object.values(caisseMocks).forEach((mock) => mock.mockReset());
    caisseMocks.listerCommandesAPayer.mockResolvedValue([commande]);
    caisseMocks.listerFactures.mockResolvedValue(pageVide);
    caisseMocks.listerPaiementsCommande.mockResolvedValue(pageVide);
  });

  it('enregistre un paiement valide sans envoyer de total et affiche le nouvel état', async () => {
    caisseMocks.enregistrerPaiement.mockResolvedValue(paiementComplet);
    const user = await ouvrirCommandeAvecApplication();
    await user.type(screen.getByLabelText('Montant'), '80');
    await user.selectOptions(screen.getByLabelText('Mode de paiement'), 'CARTE');
    await user.click(screen.getByRole('button', { name: 'Vérifier et confirmer' }));
    await user.click(screen.getByRole('button', { name: 'Confirmer le paiement' }));

    await waitFor(() => expect(caisseMocks.enregistrerPaiement).toHaveBeenCalledTimes(1));
    const [numero, cle, corps] = caisseMocks.enregistrerPaiement.mock.calls[0];
    expect(numero).toBe('CMD-CASH-001');
    expect(cle).toMatch(/^[0-9a-f-]{36}$/i);
    expect(corps).toEqual({ montant: 80, mode: 'CARTE' });
    expect(corps).not.toHaveProperty('montantTotal');
    expect(corps).not.toHaveProperty('montantRestant');
    expect(await screen.findByText('Paiement validé. La commande est payée.')).toBeInTheDocument();
    expect(screen.getByText('FAC-2026-000041')).toBeInTheDocument();
    expect(screen.queryByLabelText('Montant')).not.toBeInTheDocument();
  });

  it('refuse un montant négatif avant tout appel API', async () => {
    const user = await ouvrirCommandeAvecApplication();
    await user.type(screen.getByLabelText('Montant'), '-5');
    await user.click(screen.getByRole('button', { name: 'Vérifier et confirmer' }));
    expect(await screen.findByText('Saisissez un montant positif avec deux décimales au maximum.')).toBeInTheDocument();
    expect(caisseMocks.enregistrerPaiement).not.toHaveBeenCalled();
  });

  it('refuse un montant supérieur au restant à payer', async () => {
    const user = await ouvrirCommandeAvecApplication();
    await user.type(screen.getByLabelText('Montant'), '80.01');
    await user.click(screen.getByRole('button', { name: 'Vérifier et confirmer' }));
    expect(await screen.findByText('Le montant ne peut pas dépasser le montant restant à payer.')).toBeInTheDocument();
    expect(caisseMocks.enregistrerPaiement).not.toHaveBeenCalled();
  });

  it('neutralise une double soumission pendant la requête', async () => {
    let terminer!: (paiement: PaiementResponse) => void;
    caisseMocks.enregistrerPaiement.mockImplementation(() => new Promise<PaiementResponse>((resolve) => { terminer = resolve; }));
    const user = await ouvrirCommandeAvecApplication();
    await user.type(screen.getByLabelText('Montant'), '40');
    await user.click(screen.getByRole('button', { name: 'Vérifier et confirmer' }));
    const confirmer = screen.getByRole('button', { name: 'Confirmer le paiement' });
    await user.click(confirmer);
    expect((screen.getByRole('button', { name: 'Traitement…' }) as HTMLButtonElement).disabled).toBe(true);
    await user.click(screen.getByRole('button', { name: 'Traitement…' }));
    expect(caisseMocks.enregistrerPaiement).toHaveBeenCalledTimes(1);

    await act(async () => {
      terminer({ ...paiementComplet, montant: 40, montantPaye: 60, montantRestant: 40, statutCommande: 'PRETE', facture: null });
      await Promise.resolve();
    });
    expect(await screen.findByText('Paiement validé. Un montant reste à payer.')).toBeInTheDocument();
  });

  it('affiche le refus du backend lorsqu’une facture est demandée avant paiement complet', async () => {
    caisseMocks.consulterFacture.mockRejectedValue(new ApiError(409, 'BUSINESS_CONFLICT', 'La facture n’est disponible qu’après le paiement complet.'));
    const user = await ouvrirCommandeAvecApplication();
    await user.click(screen.getByRole('button', { name: 'Consulter la facture' }));
    expect(await screen.findByText('Cette opération a déjà été traitée ou la commande a changé : La facture n’est disponible qu’après le paiement complet.')).toBeInTheDocument();
    expect(caisseMocks.consulterFacture).toHaveBeenCalledWith('CMD-CASH-001');
  });

  it('affiche les paiements détaillés et les factures récentes', async () => {
    caisseMocks.listerFactures.mockResolvedValue({
      ...pageVide,
      contenu: [facture],
      totalElements: 1,
      totalPages: 1,
    });
    caisseMocks.listerPaiementsCommande.mockResolvedValue({
      ...pageVide,
      contenu: [paiementHistorique],
      totalElements: 1,
      totalPages: 1,
    });

    await ouvrirCommandeAvecApplication();

    expect(await screen.findByRole('heading', { name: 'Historique des paiements' })).toBeInTheDocument();
    expect(await screen.findByText(/Espèces/)).toBeInTheDocument();
    expect(screen.getByText(/FAC-2026-000041 · CMD-CASH-001/)).toBeInTheDocument();
    expect(screen.queryByText(/ne fournit pas encore l’historique détaillé/i)).not.toBeInTheDocument();
  });

  it('télécharge le PDF uniquement après obtention de la facture', async () => {
    caisseMocks.consulterFacture.mockResolvedValue(facture);
    caisseMocks.telechargerFacturePdf.mockResolvedValue(new Blob(['%PDF-1.7'], { type: 'application/pdf' }));
    const creerUrl = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:facture');
    const libererUrl = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    const clic = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    const user = await ouvrirCommandeAvecApplication();

    await user.click(screen.getByRole('button', { name: 'Consulter la facture' }));
    await user.click(await screen.findByRole('button', { name: 'Télécharger le PDF' }));

    await waitFor(() => expect(caisseMocks.telechargerFacturePdf).toHaveBeenCalledWith('CMD-CASH-001'));
    expect(creerUrl).toHaveBeenCalled();
    expect(clic).toHaveBeenCalled();
    expect(libererUrl).toHaveBeenCalledWith('blob:facture');
  });

  it('interdit la route sans la permission de caisse', async () => {
    application({
      ...utilisateurCaissier(),
      roles: ['SERVEUR'],
      permissions: ['COMMANDE_CONSULTER'],
    });
    expect(await screen.findByRole('heading', { name: 'Accès refusé' })).toBeInTheDocument();
    expect(caisseMocks.listerCommandesAPayer).not.toHaveBeenCalled();
  });
});

async function ouvrirCommandeAvecApplication() {
  application();
  return ouvrirCommande();
}
