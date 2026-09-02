import { apiRequest } from '../../api/client';
import type { CommandeCuisine } from './types';

export const cuisineApi = {
  lister: (signal?: AbortSignal) => apiRequest<CommandeCuisine[]>('/api/cuisine/commandes', { signal }),
  demarrer: (numero: string) => apiRequest<CommandeCuisine>(
    `/api/cuisine/commandes/${encodeURIComponent(numero)}/demarrage`,
    { method: 'POST', csrf: true },
  ),
  marquerPrete: (numero: string) => apiRequest<CommandeCuisine>(
    `/api/cuisine/commandes/${encodeURIComponent(numero)}/prete`,
    { method: 'POST', csrf: true },
  ),
};
