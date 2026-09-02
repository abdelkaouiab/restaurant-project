import { apiRequest } from '../../api/client';
import type { PageResponse } from '../../types/api';
import type { CommandeAPayer, Facture, PaiementHistorique } from '../caisse/types';
import type { CommandeCuisine } from '../cuisine/types';
import type { Achat, StatutAchat } from '../stock/types';
import type { UtilisateurAdministration } from '../utilisateurs/types';
import type { FiltresJournal, PageJournal, Periode, TableauBordFinancier, TableauBordOperations, UtilisateurJournal } from './types';

function query(parametres: object): string {
  const recherche = new URLSearchParams();
  Object.entries(parametres).forEach(([cle, valeur]) => {
    if (valeur !== undefined && valeur !== '') recherche.set(cle, String(valeur));
  });
  const resultat = recherche.toString();
  return resultat ? `?${resultat}` : '';
}

export const dashboardApi = {
  operations: (periode: Periode, signal?: AbortSignal) => apiRequest<TableauBordOperations>(`/api/tableau-de-bord/operations${query(periode)}`, { signal }),
  finances: (periode: Periode, signal?: AbortSignal) => apiRequest<TableauBordFinancier>(`/api/tableau-de-bord/finances${query(periode)}`, { signal }),
  journal: (filtres: FiltresJournal, signal?: AbortSignal) => apiRequest<PageJournal>(`/api/tableau-de-bord/journal${query(filtres)}`, { signal }),
  utilisateursJournal: (signal?: AbortSignal) => apiRequest<UtilisateurJournal[]>('/api/tableau-de-bord/journal/utilisateurs', { signal }),
  compterUtilisateurs: async (actif: boolean, signal?: AbortSignal) => {
    const resultat = await apiRequest<PageResponse<UtilisateurAdministration>>(`/api/admin/utilisateurs${query({ actif, page: 0, taille: 1, tri: 'email', direction: 'asc' })}`, { signal });
    return resultat.totalElements;
  },
  commandesAPayer: (signal?: AbortSignal) => apiRequest<CommandeAPayer[]>('/api/caisse/commandes-a-payer', { signal }),
  paiementsDuJour: (date: string, signal?: AbortSignal) => apiRequest<PageResponse<PaiementHistorique>>(`/api/caisse/paiements${query({ date, page: 0, taille: 5 })}`, { signal }),
  facturesRecentes: (signal?: AbortSignal) => apiRequest<PageResponse<Facture>>(`/api/caisse/factures${query({ page: 0, taille: 5 })}`, { signal }),
  commandesCuisine: (signal?: AbortSignal) => apiRequest<CommandeCuisine[]>('/api/cuisine/commandes', { signal }),
  compterAchats: async (statut: StatutAchat, signal?: AbortSignal) => {
    const resultat = await apiRequest<PageResponse<Achat>>(`/api/achats${query({ statut, page: 0, taille: 1, tri: 'creeLe', direction: 'desc' })}`, { signal });
    return resultat.totalElements;
  },
};
