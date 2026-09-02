import { apiRequest } from '../../api/client';
import type { PageResponse } from '../../types/api';
import type {
  Categorie,
  ConfigurationReferentiel,
  EntiteReferentiel,
  RechercheReferentiel,
  ReferentielPayload,
} from './types';

function query(params: object): string {
  const recherche = new URLSearchParams();
  Object.entries(params).forEach(([cle, valeur]) => {
    if (valeur !== undefined && valeur !== '') recherche.set(cle, String(valeur));
  });
  return recherche.toString();
}

async function listerCategoriesActives(signal?: AbortSignal): Promise<Categorie[]> {
  const categories: Categorie[] = [];
  let page = 0;
  let totalPages = 1;
  do {
    const resultat = await apiRequest<PageResponse<Categorie>>(
      `/api/categories?${query({ actif: true, page, taille: 100, tri: 'nom', direction: 'asc' })}`,
      { signal },
    );
    categories.push(...resultat.contenu);
    totalPages = resultat.totalPages;
    page += 1;
  } while (page < totalPages);
  return categories;
}

export const referentielsApi = {
  rechercher: (configuration: ConfigurationReferentiel, filtres: RechercheReferentiel, signal?: AbortSignal) =>
    apiRequest<PageResponse<EntiteReferentiel>>(`${configuration.endpoint}?${query(filtres)}`, { signal }),
  obtenir: (configuration: ConfigurationReferentiel, id: number, signal?: AbortSignal) =>
    apiRequest<EntiteReferentiel>(`${configuration.endpoint}/${id}`, { signal }),
  creer: (configuration: ConfigurationReferentiel, payload: ReferentielPayload) =>
    apiRequest<EntiteReferentiel>(configuration.endpoint, { method: 'POST', body: payload, csrf: true }),
  modifier: (configuration: ConfigurationReferentiel, id: number, payload: ReferentielPayload) =>
    apiRequest<EntiteReferentiel>(`${configuration.endpoint}/${id}`, { method: 'PUT', body: payload, csrf: true }),
  modifierStatut: (configuration: ConfigurationReferentiel, id: number, actif: boolean) =>
    apiRequest<EntiteReferentiel>(`${configuration.endpoint}/${id}/statut`, { method: 'PATCH', body: { actif }, csrf: true }),
  enregistrerImageProduit: (id: number, fichier: File) => {
    const formulaire = new FormData();
    formulaire.append('fichier', fichier);
    return apiRequest<EntiteReferentiel>(`/api/produits/${id}/image`, {
      method: 'PUT', body: formulaire, csrf: true,
    });
  },
  supprimerImageProduit: (id: number) =>
    apiRequest<EntiteReferentiel>(`/api/produits/${id}/image`, { method: 'DELETE', csrf: true }),
  listerCategoriesActives,
};
