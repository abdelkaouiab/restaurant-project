import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api/client';
import { AuthProvider } from '../../auth/AuthContext';
import { AppRoutes } from '../../routes/AppRoutes';
import { RouterProvider } from '../../routes/Router';
import { act, render, screen, userEvent, waitFor, within } from '../../test/dom';
import type { UtilisateurAuthentifie } from '../../types/auth';
import type { Achat, Fournisseur, Ingredient, MouvementStock, ReceptionAchatHistorique } from './types';

const authMocks = vi.hoisted(() => ({ me: vi.fn(), login: vi.fn(), logout: vi.fn() }));
const stockMocks = vi.hoisted(() => ({
  listerIngredients: vi.fn(), creerIngredient: vi.fn(), modifierIngredient: vi.fn(),
  listerProduits: vi.fn(), lireRecette: vi.fn(), definirRecette: vi.fn(), supprimerIngredientRecette: vi.fn(), creerMouvement: vi.fn(), listerMouvements: vi.fn(),
  rechercherFournisseurs: vi.fn(), creerFournisseur: vi.fn(), modifierFournisseur: vi.fn(), modifierStatutFournisseur: vi.fn(),
  rechercherAchats: vi.fn(), obtenirAchat: vi.fn(), creerAchat: vi.fn(), ajouterLigneAchat: vi.fn(),
  commanderAchat: vi.fn(), annulerAchat: vi.fn(), recevoirAchat: vi.fn(), listerReceptionsAchat: vi.fn(),
}));

vi.mock('../../api/authApi', () => ({ authApi: { utilisateurCourant: authMocks.me, connexion: authMocks.login, deconnexion: authMocks.logout } }));
vi.mock('./stockApi', () => ({ stockApi: stockMocks }));

const ingredient: Ingredient = { id: 7, nom: 'Farine', unite: 'KILOGRAMME', quantiteDisponible: 10, seuilAlerte: 2, stockFaible: false, actif: true, version: 0 };
const ingredientUnitaire: Ingredient = { id: 8, nom: 'Œufs', unite: 'UNITE', quantiteDisponible: 37, seuilAlerte: 30, stockFaible: false, actif: true, version: 0 };
const fournisseur: Fournisseur = { id: 3, nom: 'Marché central', email: null, telephone: null, adresse: null, actif: true, version: 0 };
const achatCommande: Achat = {
  id: 21, numero: 'ACH-20260803-ABC12345', fournisseur, statut: 'COMMANDE', montantTotal: 25,
  lignes: [{ id: 31, ingredientId: 7, ingredient: 'Farine', unite: 'KILOGRAMME', quantiteCommandee: 10, quantiteRecue: 0, quantiteRestante: 10, prixUnitaire: 2.5, sousTotal: 25, version: 0 }],
  commandeLe: '2026-08-03T12:00:00Z', recueLe: null, annuleLe: null,
  creeLe: '2026-08-03T11:00:00Z', modifieLe: '2026-08-03T12:00:00Z', version: 1,
};
const achatPartiel: Achat = {
  ...achatCommande, statut: 'PARTIELLEMENT_RECU', version: 2,
  lignes: [{ ...achatCommande.lignes[0], quantiteRecue: 4, quantiteRestante: 6, version: 1 }],
};
const receptionAchat: ReceptionAchatHistorique = {
  id: 41,
  creeLe: '2026-08-03T12:30:00Z',
  lignes: [{ id: 51, ligneAchatId: 31, ingredientId: 7, ingredient: 'Farine', unite: 'KILOGRAMME', quantite: 4 }],
};
const mouvement: MouvementStock = {
  id: 90, ingredientId: 7, type: 'ENTREE', quantite: 5, variation: 5, quantiteApres: 15,
  motif: null, cleIdempotence: '05e2252e-c2c8-4462-bc2d-4d37915d40b7', creeLe: '2026-08-03T12:30:00Z',
};
const pageMouvements = {
  contenu: [{ id: 90, ingredientId: 7, ingredient: 'Farine', type: 'ENTREE' as const, quantite: 5, variation: 5, quantiteApres: 15, motif: null, creeLe: '2026-08-03T12:30:00Z' }],
  page: 0, taille: 10, totalElements: 1, totalPages: 1,
};

function responsableStock(): UtilisateurAuthentifie {
  return {
    id: 15, email: 'stock@restaurant.test',
    employe: { id: 15, matricule: 'STK-15', nom: 'Amrani', prenom: 'Sara', poste: 'Responsable stock' },
    roles: ['RESPONSABLE_STOCK'],
    permissions: ['TABLEAU_BORD_CONSULTER', 'STOCK_CONSULTER', 'STOCK_GERER', 'ACHAT_CONSULTER', 'ACHAT_GERER', 'PRODUIT_CONSULTER'],
  };
}

function application(route: '/stock' | '/fournisseurs' | '/achats', utilisateur = responsableStock()) {
  authMocks.me.mockResolvedValue(utilisateur);
  return render(<RouterProvider initialPath={route}><AuthProvider><AppRoutes /></AuthProvider></RouterProvider>);
}

async function ouvrirAchat() {
  const user = userEvent.setup();
  application('/achats');
  await screen.findByText('ACH-20260803-ABC12345');
  await user.click(screen.getByRole('button', { name: 'Consulter' }));
  return user;
}

describe('interface de gestion des stocks', () => {
  beforeEach(() => {
    Object.values(authMocks).forEach((mock) => mock.mockReset());
    Object.values(stockMocks).forEach((mock) => mock.mockReset());
    stockMocks.listerIngredients.mockResolvedValue([ingredient]);
    stockMocks.listerProduits.mockResolvedValue([]);
    stockMocks.listerMouvements.mockResolvedValue(pageMouvements);
    stockMocks.rechercherFournisseurs.mockResolvedValue({ contenu: [fournisseur], page: 0, taille: 100, totalElements: 1, totalPages: 1 });
    stockMocks.rechercherAchats.mockResolvedValue({ contenu: [achatCommande], page: 0, taille: 20, totalElements: 1, totalPages: 1 });
    stockMocks.listerReceptionsAchat.mockResolvedValue([]);
  });

  it('consulte et filtre l’historique paginé des mouvements', async () => {
    application('/stock');
    const user = userEvent.setup();
    expect(await screen.findByRole('heading', { name: 'Historique des mouvements' })).toBeInTheDocument();
    expect(await screen.findByText('+5,000')).toBeInTheDocument();
    expect(screen.queryByText(/ne fournit pas de route de consultation/i)).not.toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText('Type', { selector: '#history-type' }), 'ENTREE');
    await waitFor(() => expect(stockMocks.listerMouvements).toHaveBeenLastCalledWith(
      { ingredientId: undefined, type: 'ENTREE', page: 0, taille: 10 }, expect.any(AbortSignal),
    ));
  });

  it('place le focus dans le formulaire lors de la création d’un ingrédient', async () => {
    application('/stock');
    const user = userEvent.setup();
    await screen.findByRole('heading', { name: 'Créer un ingrédient' });
    const nom = screen.getByLabelText('Nom', { selector: '#ingredient-name' });
    await user.type(nom, 'Valeur à effacer');
    await user.click(screen.getByRole('button', { name: 'Nouvel ingrédient' }));
    await waitFor(() => expect(nom).toHaveFocus());
    expect(nom).toHaveValue('');
    expect(screen.getByLabelText('Unité')).toHaveValue('KILOGRAMME');
    expect(screen.getByLabelText('Seuil d’alerte')).toHaveValue('0');
  });

  it('retire un ingrédient d’une recette après confirmation avec sa version', async () => {
    stockMocks.listerProduits.mockResolvedValue([{ id: 12, nom: 'Pain', actif: true }]);
    stockMocks.lireRecette.mockResolvedValue([{ ingredientId: 7, ingredient: 'Farine', unite: 'KILOGRAMME', quantite: 0.25, version: 3 }]);
    stockMocks.supprimerIngredientRecette.mockResolvedValue(undefined);
    application('/stock');
    const user = userEvent.setup();
    await screen.findByRole('heading', { name: 'Recettes' });
    await user.selectOptions(screen.getByLabelText('Produit'), '12');
    await user.click(await screen.findByRole('button', { name: 'Retirer Farine' }));
    expect(screen.getByText(/L’historique existant sera conservé/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Retirer de la recette' }));
    await waitFor(() => expect(stockMocks.supprimerIngredientRecette).toHaveBeenCalledWith(12, 7, 3));
    expect(await screen.findByText('Recette vide')).toBeInTheDocument();
    expect(screen.queryByText(/ne fournit aucune suppression/i)).not.toBeInTheDocument();
  });

  it('enregistre un mouvement valide avec une clé d’idempotence et sans quantité directe sur l’ingrédient', async () => {
    stockMocks.creerMouvement.mockResolvedValue(mouvement);
    stockMocks.listerIngredients.mockResolvedValueOnce([ingredient]).mockResolvedValueOnce([{ ...ingredient, quantiteDisponible: 15 }]);
    application('/stock');
    const user = userEvent.setup();
    await screen.findByRole('heading', { name: 'Ingrédients et mouvements' });
    expect(screen.queryByLabelText('Quantité disponible')).not.toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText('Ingrédient', { selector: '#movement-ingredient' }), '7');
    await user.type(screen.getByLabelText('Quantité', { selector: '#movement-quantity' }), '5');
    await user.click(screen.getByRole('button', { name: 'Vérifier le mouvement' }));
    await user.click(screen.getByRole('button', { name: 'Confirmer le mouvement' }));

    await waitFor(() => expect(stockMocks.creerMouvement).toHaveBeenCalledTimes(1));
    const [cle, payload] = stockMocks.creerMouvement.mock.calls[0];
    expect(cle).toMatch(/^[0-9a-f-]{36}$/i);
    expect(payload).toEqual({ ingredientId: 7, type: 'ENTREE', quantite: 5, motif: null });
    expect(await screen.findByText('Dernier mouvement enregistré')).toBeInTheDocument();
    expect(screen.getByText('Entrée · nouveau solde 15,000')).toBeInTheDocument();
  });

  it('affiche les quantités en unité sans décimales et refuse une saisie fractionnaire', async () => {
    stockMocks.listerIngredients.mockResolvedValue([ingredientUnitaire]);
    stockMocks.listerMouvements.mockResolvedValue({ ...pageMouvements, contenu: [], totalElements: 0, totalPages: 0 });
    application('/stock');
    const user = userEvent.setup();
    const nomOeufs = await screen.findByText('Œufs');
    const ligne = nomOeufs.closest('tr');
    expect(ligne).not.toBeNull();
    expect(within(ligne!).getByText('37')).toBeInTheDocument();
    expect(within(ligne!).getByText('30')).toBeInTheDocument();
    expect(within(ligne!).queryByText('37,000')).not.toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText('Ingrédient', { selector: '#movement-ingredient' }), '8');
    const champ = screen.getByLabelText('Quantité', { selector: '#movement-quantity' });
    expect(champ.getAttribute('step')).toBe('1');
    await user.type(champ, '1.5');
    await user.click(screen.getByRole('button', { name: 'Vérifier le mouvement' }));
    expect(await screen.findByText('Une quantité mesurée à l’unité doit être un nombre entier.')).toBeInTheDocument();
    expect(stockMocks.creerMouvement).not.toHaveBeenCalled();
  });

  it('exige le motif d’une perte avant l’appel au backend', async () => {
    application('/stock'); const user = userEvent.setup();
    await screen.findByRole('heading', { name: 'Ingrédients et mouvements' });
    await user.selectOptions(screen.getByLabelText('Ingrédient', { selector: '#movement-ingredient' }), '7');
    await user.selectOptions(screen.getByLabelText('Type'), 'PERTE');
    await user.type(screen.getByLabelText('Quantité', { selector: '#movement-quantity' }), '1');
    await user.click(screen.getByRole('button', { name: 'Vérifier le mouvement' }));
    expect(await screen.findByText('Un motif est obligatoire pour les ajustements et les pertes.')).toBeInTheDocument();
    expect(stockMocks.creerMouvement).not.toHaveBeenCalled();
  });

  it('affiche un stock insuffisant refusé par le backend', async () => {
    stockMocks.creerMouvement.mockRejectedValue(new ApiError(409, 'BUSINESS_CONFLICT', 'Stock insuffisant pour Farine.'));
    application('/stock'); const user = userEvent.setup();
    await screen.findByRole('heading', { name: 'Ingrédients et mouvements' });
    await user.selectOptions(screen.getByLabelText('Ingrédient', { selector: '#movement-ingredient' }), '7');
    await user.selectOptions(screen.getByLabelText('Type'), 'SORTIE');
    await user.type(screen.getByLabelText('Quantité', { selector: '#movement-quantity' }), '20');
    await user.click(screen.getByRole('button', { name: 'Vérifier le mouvement' }));
    await user.click(screen.getByRole('button', { name: 'Confirmer le mouvement' }));
    expect(await screen.findByText('Conflit : Stock insuffisant pour Farine.')).toBeInTheDocument();
  });

  it('ouvre le formulaire de création d’un fournisseur et place le focus sur le nom', async () => {
    application('/fournisseurs');
    const user = userEvent.setup();
    await screen.findByRole('button', { name: 'Désactiver Marché central' });
    expect(screen.queryByRole('heading', { name: 'Fournisseur' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Nouveau fournisseur' }));
    expect(await screen.findByRole('heading', { name: 'Fournisseur' })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText('Nom', { selector: '#supplier-name' })).toHaveFocus());
  });

  it('désactive un fournisseur après confirmation avec contrôle de version', async () => {
    stockMocks.modifierStatutFournisseur.mockResolvedValue({ ...fournisseur, actif: false, version: 1 });
    application('/fournisseurs');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Désactiver Marché central' }));
    expect(screen.getByText(/Les achats existants seront conservés/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Confirmer la désactivation' }));
    await waitFor(() => expect(stockMocks.modifierStatutFournisseur).toHaveBeenCalledWith(3, false, 0));
    expect(await screen.findByText('Inactif')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Réactiver Marché central' })).toBeInTheDocument();
    expect(screen.queryByText(/ne fournit pas de route d’activation/i)).not.toBeInTheDocument();
  });

  it('enregistre une réception partielle et affiche les quantités mises à jour', async () => {
    stockMocks.recevoirAchat.mockResolvedValue(achatPartiel);
    const user = await ouvrirAchat();
    await user.selectOptions(screen.getByLabelText('Ligne'), '31');
    await user.type(screen.getByLabelText('Quantité reçue'), '4');
    await user.click(screen.getByRole('button', { name: 'Préparer la réception partielle' }));
    await user.click(screen.getByRole('button', { name: 'Confirmer la réception' }));
    await waitFor(() => expect(stockMocks.recevoirAchat).toHaveBeenCalledTimes(1));
    const [achatId, cle, lignes] = stockMocks.recevoirAchat.mock.calls[0];
    expect(achatId).toBe(21); expect(cle).toMatch(/^[0-9a-f-]{36}$/i); expect(lignes).toEqual([{ ligneAchatId: 31, quantite: 4 }]);
    expect(await screen.findByText('Partiellement reçu')).toBeInTheDocument();
    expect(screen.getByText('6,000')).toBeInTheDocument();
  });

  it('affiche l’historique des réceptions sans identifiant interne', async () => {
    stockMocks.listerReceptionsAchat.mockResolvedValue([receptionAchat]);
    await ouvrirAchat();
    expect(await screen.findByRole('heading', { name: 'Historique des réceptions' })).toBeInTheDocument();
    expect(screen.queryByText('#41')).not.toBeInTheDocument();
    expect(screen.getByText('4,000')).toBeInTheDocument();
    expect(stockMocks.listerReceptionsAchat).toHaveBeenCalledWith(21, expect.any(AbortSignal));
    expect(screen.queryByText(/ne fournit pas de route listant les réceptions individuelles/i)).not.toBeInTheDocument();
  });

  it('empêche une double réception visuelle pendant la requête', async () => {
    let terminer!: (achat: Achat) => void;
    stockMocks.recevoirAchat.mockImplementation(() => new Promise<Achat>((resolve) => { terminer = resolve; }));
    const user = await ouvrirAchat();
    await user.selectOptions(screen.getByLabelText('Ligne'), '31');
    await user.type(screen.getByLabelText('Quantité reçue'), '4');
    await user.click(screen.getByRole('button', { name: 'Préparer la réception partielle' }));
    await user.click(screen.getByRole('button', { name: 'Confirmer la réception' }));
    const traitement = screen.getByRole('button', { name: 'Traitement…' });
    expect((traitement as HTMLButtonElement).disabled).toBe(true);
    await user.click(traitement);
    expect(stockMocks.recevoirAchat).toHaveBeenCalledTimes(1);
    await act(async () => { terminer(achatPartiel); await Promise.resolve(); });
    expect(await screen.findByText('Réception enregistrée et stock mis à jour.')).toBeInTheDocument();
  });

  it('refuse la route stock sans permission', async () => {
    application('/stock', { ...responsableStock(), roles: ['SERVEUR'], permissions: ['COMMANDE_CONSULTER'] });
    expect(await screen.findByRole('heading', { name: 'Accès refusé' })).toBeInTheDocument();
    expect(stockMocks.listerIngredients).not.toHaveBeenCalled();
  });

  it('affiche une erreur de concurrence lors d’une réception', async () => {
    stockMocks.recevoirAchat.mockRejectedValue(new ApiError(409, 'BUSINESS_CONFLICT', 'La quantité dépasse le restant à recevoir.'));
    const user = await ouvrirAchat();
    await user.selectOptions(screen.getByLabelText('Ligne'), '31');
    await user.type(screen.getByLabelText('Quantité reçue'), '4');
    await user.click(screen.getByRole('button', { name: 'Préparer la réception partielle' }));
    await user.click(screen.getByRole('button', { name: 'Confirmer la réception' }));
    expect(await screen.findByText('Cet achat vient d’être modifié ou l’opération a déjà été traitée : La quantité dépasse le restant à recevoir.')).toBeInTheDocument();
  });
});
