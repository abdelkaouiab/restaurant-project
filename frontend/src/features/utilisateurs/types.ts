export const NOMS_ROLES = [
  'ADMINISTRATEUR',
  'GERANT',
  'SERVEUR',
  'CAISSIER',
  'CUISINE',
  'RESPONSABLE_STOCK',
] as const;

export type NomRole = typeof NOMS_ROLES[number];

export const LIBELLES_ROLES: Record<NomRole, string> = {
  ADMINISTRATEUR: 'Administrateur',
  GERANT: 'Gérant',
  SERVEUR: 'Serveur',
  CAISSIER: 'Caissier',
  CUISINE: 'Cuisine',
  RESPONSABLE_STOCK: 'Responsable stock',
};

export interface EmployeUtilisateur {
  id: number;
  matricule: string;
  nom: string;
  prenom: string;
}

export interface UtilisateurAdministration {
  id: number;
  email: string;
  actif: boolean;
  employe: EmployeUtilisateur;
  roles: NomRole[];
  creeLe: string;
  modifieLe: string;
  version: number;
}

export interface EmployeDisponible extends EmployeUtilisateur {
  telephone: string | null;
  poste: string;
  dateEmbauche: string;
  actif: boolean;
  creeLe: string;
  modifieLe: string;
  version: number;
}

export interface RechercheUtilisateurs {
  recherche?: string;
  actif?: boolean;
  page: number;
  taille: number;
  tri: ChampTriUtilisateur;
  direction: DirectionTri;
}

export type ChampTriUtilisateur = 'email' | 'nomEmploye' | 'creeLe' | 'modifieLe';
export type DirectionTri = 'asc' | 'desc';

export interface CreerUtilisateurPayload {
  email: string;
  motDePasse: string;
  employeId: number;
  roles: NomRole[];
}

