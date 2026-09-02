import { apiRequest, effacerCsrf } from './client';
import type { IdentifiantsConnexion, UtilisateurAuthentifie } from '../types/auth';

export const authApi = {
  utilisateurCourant: () => apiRequest<UtilisateurAuthentifie>('/api/auth/me', { ignorerExpirationSession: true }),
  async connexion(identifiants: IdentifiantsConnexion) {
    const utilisateur = await apiRequest<UtilisateurAuthentifie>('/api/auth/login', {
      method: 'POST', body: identifiants, csrf: true, ignorerExpirationSession: true,
    });
    effacerCsrf();
    return utilisateur;
  },
  async deconnexion() {
    await apiRequest<void>('/api/auth/logout', { method: 'POST', csrf: true });
    effacerCsrf();
  },
};
