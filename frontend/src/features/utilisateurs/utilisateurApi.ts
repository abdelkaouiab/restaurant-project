import { apiRequest } from '../../api/client';
import type { PageResponse } from '../../types/api';
import type {
  CreerUtilisateurPayload,
  EmployeDisponible,
  NomRole,
  RechercheUtilisateurs,
  UtilisateurAdministration,
} from './types';

function query(params: object): string {
  const recherche = new URLSearchParams();
  Object.entries(params).forEach(([cle, valeur]) => {
    if (valeur !== undefined && valeur !== '') recherche.set(cle, String(valeur));
  });
  return recherche.toString();
}

async function rechercherUtilisateurs(
  filtres: RechercheUtilisateurs,
  signal?: AbortSignal,
): Promise<PageResponse<UtilisateurAdministration>> {
  return apiRequest(`/api/admin/utilisateurs?${query(filtres)}`, { signal });
}

async function listerEmployesActifs(): Promise<EmployeDisponible[]> {
  const employes: EmployeDisponible[] = [];
  let page = 0;
  let totalPages = 1;
  do {
    const resultat = await apiRequest<PageResponse<EmployeDisponible>>(
      `/api/employes?${query({ actif: true, page, taille: 100, tri: 'nom', direction: 'asc' })}`,
    );
    employes.push(...resultat.contenu);
    totalPages = resultat.totalPages;
    page += 1;
  } while (page < totalPages);
  return employes;
}

async function compterAdministrateursActifs(): Promise<number> {
  let total = 0;
  let page = 0;
  let totalPages = 1;
  do {
    const resultat = await rechercherUtilisateurs({
      actif: true,
      page,
      taille: 100,
      tri: 'email',
      direction: 'asc',
    });
    total += resultat.contenu.filter((utilisateur) =>
      utilisateur.roles.includes('ADMINISTRATEUR')).length;
    totalPages = resultat.totalPages;
    page += 1;
  } while (page < totalPages);
  return total;
}

export const utilisateurApi = {
  rechercher: rechercherUtilisateurs,
  creer: (payload: CreerUtilisateurPayload) => apiRequest<UtilisateurAdministration>(
    '/api/admin/utilisateurs',
    { method: 'POST', body: payload, csrf: true },
  ),
  modifier: (id: number, email: string) => apiRequest<UtilisateurAdministration>(
    `/api/admin/utilisateurs/${id}`,
    { method: 'PUT', body: { email }, csrf: true },
  ),
  modifierRoles: (id: number, roles: NomRole[]) => apiRequest<UtilisateurAdministration>(
    `/api/admin/utilisateurs/${id}/roles`,
    { method: 'PUT', body: { roles }, csrf: true },
  ),
  modifierStatut: (id: number, actif: boolean) => apiRequest<UtilisateurAdministration>(
    `/api/admin/utilisateurs/${id}/statut`,
    { method: 'PATCH', body: { actif }, csrf: true },
  ),
  reinitialiserMotDePasse: (id: number, nouveauMotDePasse: string) => apiRequest<void>(
    `/api/admin/utilisateurs/${id}/mot-de-passe/reinitialisation`,
    { method: 'POST', body: { nouveauMotDePasse }, csrf: true },
  ),
  listerEmployesActifs,
  compterAdministrateursActifs,
};
