export type ModePaiement = 'ESPECES' | 'CARTE' | 'CHEQUE' | 'TITRE_RESTAURANT';
export type StatutPaiement = 'VALIDE';
export type StatutCommandeCaisse = 'PRETE' | 'PAYEE';

export interface CommandeAPayer {
  numeroCommande: string;
  table: number;
  statutCommande: StatutCommandeCaisse;
  montantTotal: number;
  montantPaye: number;
  montantRestant: number;
  heure: string;
}

export interface Facture {
  numero: string;
  numeroCommande: string;
  montantTotal: number;
  emiseLe: string;
}

export interface PaiementResponse {
  id: number;
  cleIdempotence: string;
  numeroCommande: string;
  montant: number;
  mode: ModePaiement;
  statut: StatutPaiement;
  creeLe: string;
  montantPaye: number;
  montantRestant: number;
  statutCommande: StatutCommandeCaisse;
  facture: Facture | null;
}

export interface PaiementHistorique {
  id: number;
  numeroCommande: string;
  montant: number;
  mode: ModePaiement;
  statut: StatutPaiement;
  creeLe: string;
}

export interface EnregistrerPaiementRequest {
  montant: number;
  mode: ModePaiement;
}
