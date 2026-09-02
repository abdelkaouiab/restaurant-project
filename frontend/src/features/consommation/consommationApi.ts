import { apiBlob, apiRequest } from '../../api/client';
import type { Consommation } from './types';

function parametres(debut: string, fin: string): string {
  return new URLSearchParams({ debut, fin }).toString();
}

export const consommationApi = {
  consulter: (debut: string, fin: string, signal?: AbortSignal) =>
    apiRequest<Consommation>(`/api/consommations?${parametres(debut, fin)}`, { signal }),
  telechargerPdf: (debut: string, fin: string) =>
    apiBlob(`/api/consommations/rapport.pdf?${parametres(debut, fin)}`),
};
