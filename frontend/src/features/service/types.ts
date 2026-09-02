import type { Client, Produit, TableRestaurantSimple } from '../referentiels/types';

export type StatutCommande = 'BROUILLON' | 'CONFIRMEE' | 'EN_PREPARATION' | 'PRETE' | 'PAYEE' | 'ANNULEE';
export type StatutReservation = 'EN_ATTENTE' | 'CONFIRMEE' | 'ARRIVEE' | 'TERMINEE' | 'ANNULEE' | 'ABSENTE';

export interface LigneCommande {
  id: number;
  produitId: number;
  produitNom: string;
  quantite: number;
  prixUnitaire: number;
  sousTotal: number;
  version: number;
}

export interface Commande {
  id: number;
  numero: string;
  statut: StatutCommande;
  table: TableRestaurantSimple;
  client: Client | null;
  creeParId: number;
  creeParEmail: string;
  lignes: LigneCommande[];
  montantTotal: number;
  confirmeeLe: string | null;
  annuleeLe: string | null;
  creeLe: string;
  modifieLe: string;
  version: number;
}

export interface Reservation {
  id: number;
  numero: string;
  tableId: number;
  numeroTable: number;
  clientId: number;
  nombrePersonnes: number;
  debut: string;
  fin: string;
  statut: StatutReservation;
  remarques: string | null;
  numeroCommande: string | null;
  version: number;
}

export interface CreerReservationPayload {
  tableId: number;
  clientId: number;
  nombrePersonnes: number;
  debut: string;
  remarques: string | null;
}

export interface CreerCommandePayload { tableId: number; clientId?: number; }
export interface AssociationCommandePayload { tableId: number; clientId?: number; }
export interface AjouterLignePayload { produitId: number; quantite: number; remarque?: string; }
export interface ModifierLignePayload { quantite: number; remarque?: string; }

export interface DonneesSelectionService {
  tables: TableRestaurantSimple[];
  clients: Client[];
  produits: Produit[];
}
