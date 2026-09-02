import type { UtilisateurAuthentifie } from '../types/auth';

export type RouteSectionId =
  | 'tableau-de-bord'
  | 'utilisateurs'
  | 'employes'
  | 'clients'
  | 'tables'
  | 'categories'
  | 'produits'
  | 'commandes'
  | 'cuisine'
  | 'caisse'
  | 'reservations'
  | 'stock'
  | 'fournisseurs'
  | 'achats'
  | 'rapports'
  | 'consommation'
  | 'journal-activite';

export interface RouteSection {
  id: RouteSectionId;
  path: string;
  label: string;
  description: string;
  permissionsRequises: string[];
  rolesAutorises?: string[];
  groupe: 'Pilotage' | 'Restaurant' | 'Opérations' | 'Administration';
  repere: string;
}

export const ROUTES_SECTIONS: readonly RouteSection[] = [
  { id: 'tableau-de-bord', path: '/tableau-de-bord', label: 'Tableau de bord', description: 'Vue synthétique de l’activité du restaurant.', permissionsRequises: ['TABLEAU_BORD_CONSULTER'], groupe: 'Pilotage', repere: 'TB' },
  { id: 'utilisateurs', path: '/utilisateurs', label: 'Utilisateurs', description: 'Gestion des comptes et de leurs accès.', permissionsRequises: ['UTILISATEUR_CONSULTER'], groupe: 'Administration', repere: 'UT' },
  { id: 'employes', path: '/employes', label: 'Employés', description: 'Répertoire des membres de l’équipe.', permissionsRequises: ['EMPLOYE_CONSULTER'], groupe: 'Administration', repere: 'EM' },
  { id: 'clients', path: '/clients', label: 'Clients', description: 'Gestion des informations clients.', permissionsRequises: ['CLIENT_CONSULTER'], groupe: 'Restaurant', repere: 'CL' },
  { id: 'tables', path: '/tables', label: 'Tables', description: 'Organisation des tables et de leurs capacités.', permissionsRequises: ['TABLE_CONSULTER'], groupe: 'Restaurant', repere: 'TA' },
  { id: 'categories', path: '/categories', label: 'Catégories', description: 'Organisation du catalogue de produits.', permissionsRequises: ['CATEGORIE_CONSULTER'], groupe: 'Restaurant', repere: 'CA' },
  { id: 'produits', path: '/produits', label: 'Produits', description: 'Consultation du catalogue du restaurant.', permissionsRequises: ['PRODUIT_CONSULTER'], groupe: 'Restaurant', repere: 'PR' },
  { id: 'commandes', path: '/commandes', label: 'Commandes', description: 'Suivi du cycle de vie des commandes.', permissionsRequises: ['COMMANDE_CONSULTER'], groupe: 'Opérations', repere: 'CO' },
  { id: 'cuisine', path: '/cuisine', label: 'Cuisine', description: 'File de préparation destinée à la cuisine.', permissionsRequises: ['CUISINE_COMMANDES_GERER'], groupe: 'Opérations', repere: 'CU' },
  { id: 'caisse', path: '/caisse', label: 'Caisse', description: 'Encaissements et factures des commandes.', permissionsRequises: ['CAISSE_PAIEMENTS_GERER'], groupe: 'Opérations', repere: 'CI' },
  { id: 'reservations', path: '/reservations', label: 'Réservations', description: 'Planification des réservations de tables.', permissionsRequises: ['RESERVATION_GERER'], groupe: 'Opérations', repere: 'RE' },
  { id: 'stock', path: '/stock', label: 'Stock', description: 'Ingrédients, recettes et mouvements de stock.', permissionsRequises: ['STOCK_CONSULTER'], groupe: 'Opérations', repere: 'ST' },
  { id: 'fournisseurs', path: '/fournisseurs', label: 'Fournisseurs', description: 'Répertoire des partenaires fournisseurs.', permissionsRequises: ['ACHAT_CONSULTER'], groupe: 'Opérations', repere: 'FO' },
  { id: 'achats', path: '/achats', label: 'Achats', description: 'Commandes et réceptions auprès des fournisseurs.', permissionsRequises: ['ACHAT_CONSULTER'], groupe: 'Opérations', repere: 'AC' },
  { id: 'rapports', path: '/rapports', label: 'Rapports', description: 'Indicateurs financiers réservés à la direction.', permissionsRequises: ['TABLEAU_BORD_FINANCIER_CONSULTER'], rolesAutorises: ['ADMINISTRATEUR', 'GERANT'], groupe: 'Pilotage', repere: 'RA' },
  { id: 'consommation', path: '/consommation', label: 'Consommation', description: 'Produits consommés et ingrédients utilisés par période.', permissionsRequises: [], rolesAutorises: ['ADMINISTRATEUR', 'GERANT'], groupe: 'Pilotage', repere: 'CS' },
  { id: 'journal-activite', path: '/journal-activite', label: 'Journal d’activité', description: 'Historique des opérations réalisées dans l’application.', permissionsRequises: ['JOURNAL_ACTIVITE_CONSULTER'], groupe: 'Administration', repere: 'JA' },
] as const;

export function trouverRouteSection(path: string): RouteSection | undefined {
  return ROUTES_SECTIONS.find((route) => route.path === path);
}

export function utilisateurAutorisePourRoute(
  utilisateur: UtilisateurAuthentifie | null,
  route: RouteSection,
): boolean {
  if (!utilisateur) return false;
  const permissionsValides = route.permissionsRequises.every((permission) =>
    utilisateur.permissions.includes(permission),
  );
  const roleValide = !route.rolesAutorises
    || route.rolesAutorises.some((role) => utilisateur.roles.includes(role));
  return permissionsValides && roleValide;
}
