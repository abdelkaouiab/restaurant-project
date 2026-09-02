export interface ConsommationProduit {
  produitId: number;
  produit: string;
  quantite: number;
}

export interface ConsommationIngredient {
  ingredientId: number;
  ingredient: string;
  quantite: number;
  unite: 'GRAMME' | 'KILOGRAMME' | 'MILLILITRE' | 'LITRE' | 'UNITE';
}

export interface Consommation {
  debut: string;
  fin: string;
  produits: ConsommationProduit[];
  ingredients: ConsommationIngredient[];
}
