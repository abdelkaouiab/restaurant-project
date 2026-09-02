import { apiRequest } from '../../api/client';
import type { PageResponse } from '../../types/api';
import type { Produit } from '../referentiels/types';
import type {
  Achat,
  Fournisseur,
  FournisseurPayload,
  Ingredient,
  IngredientPayload,
  MouvementStock,
  PageAchats,
  PageFournisseurs,
  PageMouvementsStock,
  RecetteIngredient,
  ReceptionAchatHistorique,
  StatutAchat,
  TypeMouvement,
} from './types';

function query(parametres: Record<string, string | number | undefined>): string {
  const recherche = new URLSearchParams();
  Object.entries(parametres).forEach(([cle, valeur]) => {
    if (valeur !== undefined && valeur !== '') recherche.set(cle, String(valeur));
  });
  return recherche.toString();
}

async function listerTousProduits(signal?: AbortSignal): Promise<Produit[]> {
  const produits: Produit[] = [];
  let page = 0;
  let totalPages = 1;
  do {
    const resultat = await apiRequest<PageResponse<Produit>>(
      `/api/produits?${query({ actif: 'true', page, taille: 100, tri: 'nom', direction: 'asc' })}`,
      { signal },
    );
    produits.push(...resultat.contenu);
    totalPages = resultat.totalPages;
    page += 1;
  } while (page < totalPages);
  return produits;
}

export const stockApi = {
  listerIngredients: (signal?: AbortSignal) => apiRequest<Ingredient[]>('/api/stock/ingredients', { signal }),
  creerIngredient: (payload: IngredientPayload) => apiRequest<Ingredient>('/api/stock/ingredients', {
    method: 'POST', body: payload, csrf: true,
  }),
  modifierIngredient: (id: number, payload: IngredientPayload) => apiRequest<Ingredient>(`/api/stock/ingredients/${id}`, {
    method: 'PUT', body: payload, csrf: true,
  }),
  listerProduits: listerTousProduits,
  lireRecette: (produitId: number, signal?: AbortSignal) => apiRequest<RecetteIngredient[]>(`/api/stock/produits/${produitId}/recette`, { signal }),
  definirRecette: (produitId: number, ingredientId: number, quantite: number) => apiRequest<RecetteIngredient>(`/api/stock/produits/${produitId}/recette`, {
    method: 'PUT', body: { ingredientId, quantite }, csrf: true,
  }),
  supprimerIngredientRecette: (produitId: number, ingredientId: number, version: number) => apiRequest<void>(`/api/stock/produits/${produitId}/recette/${ingredientId}?${query({ version })}`, {
    method: 'DELETE', csrf: true,
  }),
  creerMouvement: (cleIdempotence: string, payload: { ingredientId: number; type: TypeMouvement; quantite: number; motif: string | null }) => apiRequest<MouvementStock>('/api/stock/mouvements', {
    method: 'POST', headers: { 'Idempotency-Key': cleIdempotence }, body: payload, csrf: true,
  }),
  listerMouvements: (filtres: { ingredientId?: number; type?: TypeMouvement; page: number; taille: number }, signal?: AbortSignal) => apiRequest<PageMouvementsStock>(`/api/stock/mouvements?${query(filtres)}`, { signal }),
  rechercherFournisseurs: (filtres: { recherche?: string; page: number; taille: number; tri: 'nom' | 'creeLe'; direction: 'asc' | 'desc' }, signal?: AbortSignal) => apiRequest<PageFournisseurs>(`/api/achats/fournisseurs?${query(filtres)}`, { signal }),
  creerFournisseur: (payload: FournisseurPayload) => apiRequest<Fournisseur>('/api/achats/fournisseurs', { method: 'POST', body: payload, csrf: true }),
  modifierFournisseur: (id: number, payload: FournisseurPayload) => apiRequest<Fournisseur>(`/api/achats/fournisseurs/${id}`, { method: 'PUT', body: payload, csrf: true }),
  modifierStatutFournisseur: (id: number, actif: boolean, version: number) => apiRequest<Fournisseur>(`/api/achats/fournisseurs/${id}/statut`, { method: 'PATCH', body: { actif, version }, csrf: true }),
  rechercherAchats: (filtres: { recherche?: string; statut?: StatutAchat; page: number; taille: number; tri: 'numero' | 'statut' | 'montantTotal' | 'creeLe'; direction: 'asc' | 'desc' }, signal?: AbortSignal) => apiRequest<PageAchats>(`/api/achats?${query(filtres)}`, { signal }),
  obtenirAchat: (id: number, signal?: AbortSignal) => apiRequest<Achat>(`/api/achats/${id}`, { signal }),
  listerReceptionsAchat: (id: number, signal?: AbortSignal) => apiRequest<ReceptionAchatHistorique[]>(`/api/achats/${id}/receptions`, { signal }),
  creerAchat: (fournisseurId: number) => apiRequest<Achat>('/api/achats', { method: 'POST', body: { fournisseurId }, csrf: true }),
  ajouterLigneAchat: (achatId: number, payload: { ingredientId: number; quantite: number; prixUnitaire: number }) => apiRequest<Achat>(`/api/achats/${achatId}/lignes`, { method: 'POST', body: payload, csrf: true }),
  commanderAchat: (achatId: number) => apiRequest<Achat>(`/api/achats/${achatId}/commande`, { method: 'POST', csrf: true }),
  annulerAchat: (achatId: number) => apiRequest<Achat>(`/api/achats/${achatId}/annulation`, { method: 'POST', csrf: true }),
  recevoirAchat: (achatId: number, cleIdempotence: string, lignes: { ligneAchatId: number; quantite: number }[]) => apiRequest<Achat>(`/api/achats/${achatId}/receptions`, {
    method: 'POST', headers: { 'Idempotency-Key': cleIdempotence }, body: { lignes }, csrf: true,
  }),
};
