export type EtatCommandeCuisine = 'CONFIRMEE' | 'EN_PREPARATION' | 'PRETE';

export interface ProduitCuisine {
  produit: string;
  quantite: number;
  remarque: string | null;
}

export interface CommandeCuisine {
  numero: string;
  table: number;
  produits: ProduitCuisine[];
  heure: string;
  etat: EtatCommandeCuisine;
}
