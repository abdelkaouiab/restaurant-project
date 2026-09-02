import type { PageResponse } from '../../types/api';

export type UniteMesure = 'GRAMME' | 'KILOGRAMME' | 'MILLILITRE' | 'LITRE' | 'UNITE';
export type TypeMouvement = 'ENTREE' | 'SORTIE' | 'AJUSTEMENT' | 'PERTE' | 'RETOUR';
export type StatutAchat = 'BROUILLON' | 'COMMANDE' | 'PARTIELLEMENT_RECU' | 'RECU' | 'ANNULE';

export interface Ingredient {
  id: number;
  nom: string;
  unite: UniteMesure;
  quantiteDisponible: number;
  seuilAlerte: number;
  stockFaible: boolean;
  actif: boolean;
  version: number;
}

export interface IngredientPayload {
  nom: string;
  unite: UniteMesure;
  seuilAlerte: number;
}

export interface ProduitStock {
  id: number;
  nom: string;
  actif: boolean;
}

export interface RecetteIngredient {
  ingredientId: number;
  ingredient: string;
  unite: UniteMesure;
  quantite: number;
  version: number;
}

export interface MouvementStock {
  id: number;
  ingredientId: number;
  type: TypeMouvement;
  quantite: number;
  variation: number;
  quantiteApres: number;
  motif: string | null;
  cleIdempotence: string;
  creeLe: string;
}

export interface MouvementStockHistorique {
  id: number;
  ingredientId: number;
  ingredient: string;
  type: TypeMouvement;
  quantite: number;
  variation: number;
  quantiteApres: number;
  motif: string | null;
  creeLe: string;
}

export interface Fournisseur {
  id: number;
  nom: string;
  email: string | null;
  telephone: string | null;
  adresse: string | null;
  actif: boolean;
  version: number;
}

export interface FournisseurPayload {
  nom: string;
  email: string | null;
  telephone: string | null;
  adresse: string | null;
}

export interface LigneAchat {
  id: number;
  ingredientId: number;
  ingredient: string;
  unite: UniteMesure;
  quantiteCommandee: number;
  quantiteRecue: number;
  quantiteRestante: number;
  prixUnitaire: number;
  sousTotal: number;
  version: number;
}

export interface Achat {
  id: number;
  numero: string;
  fournisseur: Fournisseur;
  statut: StatutAchat;
  montantTotal: number;
  lignes: LigneAchat[];
  commandeLe: string | null;
  recueLe: string | null;
  annuleLe: string | null;
  creeLe: string;
  modifieLe: string;
  version: number;
}

export interface LigneReceptionAchatHistorique {
  id: number;
  ligneAchatId: number;
  ingredientId: number;
  ingredient: string;
  unite: UniteMesure;
  quantite: number;
}

export interface ReceptionAchatHistorique {
  id: number;
  creeLe: string;
  lignes: LigneReceptionAchatHistorique[];
}

export type PageFournisseurs = PageResponse<Fournisseur>;
export type PageAchats = PageResponse<Achat>;
export type PageMouvementsStock = PageResponse<MouvementStockHistorique>;
