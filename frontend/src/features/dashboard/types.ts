import type { PageResponse } from '../../types/api';
import type { CommandeAPayer, Facture, PaiementHistorique } from '../caisse/types';
import type { CommandeCuisine } from '../cuisine/types';

export interface IndicateurValeur { libelle: string; valeur: number; }
export interface ProduitVendu { produitId: number; produit: string; quantite: number; }
export interface StockFaibleDashboard {
  ingredientId: number;
  ingredient: string;
  unite: string;
  quantiteDisponible: number;
  seuilAlerte: number;
}
export interface TableauBordOperations {
  debut: string;
  fin: string;
  nombreCommandes: number;
  nombreTablesDisponibles: number;
  commandesParEtat: IndicateurValeur[];
  produitsLesPlusVendus: ProduitVendu[];
  reservations: IndicateurValeur[];
  stocksFaibles: StockFaibleDashboard[];
}
export interface TableauBordFinancier {
  debut: string;
  fin: string;
  chiffreAffaires: number;
  paiementsParMode: IndicateurValeur[];
  depensesAchat: number;
}
export type ResultatActivite = 'SUCCES' | 'ECHEC';
export interface JournalActivite {
  id: number;
  utilisateurId: number | null;
  utilisateurNom: string | null;
  action: string;
  typeRessource: string;
  ressourceId: string | null;
  resultat: ResultatActivite;
  correlationId: string;
  creeLe: string;
}
export interface UtilisateurJournal {
  id: number;
  email: string;
  nom: string;
  prenom: string;
  actif: boolean;
}
export interface Periode { debut?: string; fin?: string; }
export interface FiltresJournal extends Periode {
  action?: string;
  typeRessource?: string;
  resultat?: ResultatActivite;
  utilisateurId?: number;
  page: number;
  taille: number;
  tri: 'creeLe' | 'action' | 'typeRessource' | 'resultat';
  direction: 'asc' | 'desc';
}
export interface DonneesDashboard {
  operations?: TableauBordOperations;
  finances?: TableauBordFinancier;
  utilisateursActifs?: number;
  utilisateursInactifs?: number;
  evenementsSecurite?: JournalActivite[];
  commandesAPayer?: CommandeAPayer[];
  paiementsDuJour?: PageResponse<PaiementHistorique>;
  facturesRecentes?: PageResponse<Facture>;
  commandesCuisine?: CommandeCuisine[];
  achatsCommandes?: number;
  achatsPartiellementRecus?: number;
}
export type PageJournal = PageResponse<JournalActivite>;
