import { apiBlob, apiRequest } from '../../api/client';
import type { PageResponse } from '../../types/api';
import type { CommandeAPayer, EnregistrerPaiementRequest, Facture, PaiementHistorique, PaiementResponse } from './types';

export const caisseApi = {
  listerCommandesAPayer: (signal?: AbortSignal) => apiRequest<CommandeAPayer[]>(
    '/api/caisse/commandes-a-payer',
    { signal },
  ),
  enregistrerPaiement: (
    numeroCommande: string,
    cleIdempotence: string,
    paiement: EnregistrerPaiementRequest,
  ) => apiRequest<PaiementResponse>(
    `/api/caisse/commandes/${encodeURIComponent(numeroCommande)}/paiements`,
    {
      method: 'POST',
      csrf: true,
      headers: { 'Idempotency-Key': cleIdempotence },
      body: paiement,
    },
  ),
  consulterFacture: (numeroCommande: string) => apiRequest<Facture>(
    `/api/caisse/commandes/${encodeURIComponent(numeroCommande)}/facture`,
  ),
  listerPaiementsCommande: (numeroCommande: string, signal?: AbortSignal) => apiRequest<PageResponse<PaiementHistorique>>(
    `/api/caisse/commandes/${encodeURIComponent(numeroCommande)}/paiements?page=0&taille=20`,
    { signal },
  ),
  listerFactures: (signal?: AbortSignal) => apiRequest<PageResponse<Facture>>(
    '/api/caisse/factures?page=0&taille=10',
    { signal },
  ),
  telechargerFacturePdf: (numeroCommande: string) => apiBlob(
    `/api/caisse/commandes/${encodeURIComponent(numeroCommande)}/facture.pdf`,
  ),
};
