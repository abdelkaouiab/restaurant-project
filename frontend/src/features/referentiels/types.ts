import type { PageResponse } from '../../types/api';

export type ModuleReferentielId = 'employes' | 'clients' | 'tables' | 'categories' | 'produits';
export type DirectionTri = 'asc' | 'desc';

export interface EntiteActive {
  id: number;
  actif: boolean;
  creeLe: string;
  modifieLe: string;
  version: number;
}

export interface Employe extends EntiteActive {
  matricule: string;
  nom: string;
  prenom: string;
  telephone: string | null;
  poste: string;
  dateEmbauche: string;
}

export interface Client extends EntiteActive {
  nom: string;
  prenom: string;
  email: string | null;
  telephone: string | null;
}

export interface TableRestaurantSimple extends EntiteActive {
  numero: number;
  capacite: number;
}

export interface TableRestaurant extends TableRestaurantSimple {
  statutOperationnel: 'LIBRE' | 'RESERVEE' | 'OCCUPEE' | 'HORS_SERVICE';
  disponible: boolean;
  commandeAssociee: {
    id: number;
    numero: string;
    statut: 'BROUILLON' | 'CONFIRMEE' | 'EN_PREPARATION' | 'PRETE';
  } | null;
  reservationEnCours: {
    id: number;
    numero: string;
    statut: 'EN_ATTENTE' | 'CONFIRMEE' | 'ARRIVEE';
    nombrePersonnes: number;
    debut: string;
    fin: string;
  } | null;
}

export interface Categorie extends EntiteActive {
  nom: string;
  description: string | null;
}

export interface Produit extends EntiteActive {
  nom: string;
  description: string | null;
  prix: number;
  categorie: Categorie;
  disponible: boolean;
  imageUrl: string | null;
  imageTypeMime: 'image/png' | 'image/jpeg' | 'image/webp' | null;
}

export type EntiteReferentiel = Employe | Client | TableRestaurant | Categorie | Produit;

export interface EmployePayload {
  matricule: string;
  nom: string;
  prenom: string;
  telephone: string | null;
  poste: string;
  dateEmbauche: string;
}

export interface ClientPayload {
  nom: string;
  prenom: string;
  email: string | null;
  telephone: string | null;
}

export interface TableRestaurantPayload { numero: number; capacite: number; }
export interface CategoriePayload { nom: string; description: string | null; }
export interface ProduitPayload { nom: string; description: string | null; prix: number; categorieId: number; disponible: boolean; }
export type ReferentielPayload = EmployePayload | ClientPayload | TableRestaurantPayload | CategoriePayload | ProduitPayload;

export interface RechercheReferentiel {
  recherche?: string;
  actif?: boolean;
  categorieId?: number;
  disponible?: boolean;
  page: number;
  taille: number;
  tri: string;
  direction: DirectionTri;
}

export type PageReferentiel = PageResponse<EntiteReferentiel>;

export interface OptionTri { valeur: string; libelle: string; }

export interface ConfigurationReferentiel {
  id: ModuleReferentielId;
  endpoint: string;
  titre: string;
  singulier: string;
  article: string;
  demonstratif: 'ce' | 'cet' | 'cette';
  aucun: string;
  description: string;
  permissionConsulter: string;
  permissionGerer: string;
  placeholderRecherche: string;
  rechercheMax: number;
  triDefaut: string;
  tris: readonly OptionTri[];
}
