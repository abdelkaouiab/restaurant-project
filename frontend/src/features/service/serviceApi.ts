import { apiRequest } from '../../api/client';
import type { PageResponse } from '../../types/api';
import type { Client, Produit, TableRestaurantSimple } from '../referentiels/types';
import type {
  AjouterLignePayload,
  AssociationCommandePayload,
  Commande,
  CreerCommandePayload,
  CreerReservationPayload,
  ModifierLignePayload,
  Reservation,
  StatutCommande,
  StatutReservation,
} from './types';

function query(params: object): string {
  const resultat = new URLSearchParams();
  Object.entries(params).forEach(([cle, valeur]) => {
    if (valeur !== undefined && valeur !== '') resultat.set(cle, String(valeur));
  });
  return resultat.toString();
}

async function toutCharger<T>(
  endpoint: string,
  parametres: Record<string, string | number | boolean>,
  signal?: AbortSignal,
): Promise<T[]> {
  const contenu: T[] = [];
  let page = 0;
  let totalPages = 1;
  do {
    const resultat = await apiRequest<PageResponse<T>>(
      `${endpoint}?${query({ ...parametres, page, taille: 100 })}`,
      { signal },
    );
    contenu.push(...resultat.contenu);
    totalPages = resultat.totalPages;
    page += 1;
  } while (page < totalPages);
  return contenu;
}

export const serviceApi = {
  listerCommandes: (filtres: { recherche?: string; statut?: StatutCommande; page: number; taille: number; tri: string; direction: 'asc' | 'desc' }, signal?: AbortSignal) =>
    apiRequest<PageResponse<Commande>>(`/api/commandes?${query(filtres)}`, { signal }),
  obtenirCommande: (id: number, signal?: AbortSignal) => apiRequest<Commande>(`/api/commandes/${id}`, { signal }),
  creerCommande: (payload: CreerCommandePayload) => apiRequest<Commande>('/api/commandes', { method: 'POST', body: payload, csrf: true }),
  modifierAssociation: (id: number, payload: AssociationCommandePayload) => apiRequest<Commande>(`/api/commandes/${id}/association`, { method: 'PUT', body: payload, csrf: true }),
  ajouterLigne: (id: number, payload: AjouterLignePayload) => apiRequest<Commande>(`/api/commandes/${id}/lignes`, { method: 'POST', body: payload, csrf: true }),
  modifierLigne: (id: number, ligneId: number, payload: ModifierLignePayload) => apiRequest<Commande>(`/api/commandes/${id}/lignes/${ligneId}`, { method: 'PUT', body: payload, csrf: true }),
  supprimerLigne: (id: number, ligneId: number) => apiRequest<Commande>(`/api/commandes/${id}/lignes/${ligneId}`, { method: 'DELETE', csrf: true }),
  confirmerCommande: (id: number) => apiRequest<Commande>(`/api/commandes/${id}/confirmation`, { method: 'POST', csrf: true }),
  annulerCommande: (id: number) => apiRequest<Commande>(`/api/commandes/${id}/annulation`, { method: 'POST', csrf: true }),

  listerReservations: (filtres: { date?: string; statut?: StatutReservation; page: number; taille: number }, signal?: AbortSignal) =>
    apiRequest<PageResponse<Reservation>>(`/api/reservations?${query(filtres)}`, { signal }),
  creerReservation: (payload: CreerReservationPayload) => apiRequest<Reservation>('/api/reservations', { method: 'POST', body: payload, csrf: true }),
  transitionReservation: (id: number, action: 'confirmation' | 'arrivee' | 'terminaison' | 'annulation' | 'absence') =>
    apiRequest<Reservation>(`/api/reservations/${id}/${action}`, { method: 'POST', csrf: true }),
  creerCommandeReservation: (id: number) => apiRequest<Reservation>(`/api/reservations/${id}/commande`, { method: 'POST', csrf: true }),

  listerTablesActives: (signal?: AbortSignal) => toutCharger<TableRestaurantSimple>('/api/tables', { actif: true, tri: 'numero', direction: 'asc' }, signal),
  listerClientsActifs: (signal?: AbortSignal) => toutCharger<Client>('/api/clients', { actif: true, tri: 'nom', direction: 'asc' }, signal),
  listerProduitsActifs: (signal?: AbortSignal) => toutCharger<Produit>('/api/produits', { actif: true, disponible: true, tri: 'nom', direction: 'asc' }, signal),
};
