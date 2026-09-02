import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  apiBlob,
  apiRequest,
  definirGestionnaireExpirationSession,
  effacerCsrf,
} from './client';

describe('client API sécurisé', () => {
  beforeEach(() => {
    effacerCsrf();
    definirGestionnaireExpirationSession(null);
    vi.restoreAllMocks();
  });

  it('transmet les cookies et ajoute le jeton CSRF aux écritures', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName:'X-XSRF-TOKEN', parameterName:'_csrf', token:'csrf-test' }),{status:200,headers:{'Content-Type':'application/json'}}))
      .mockResolvedValueOnce(new Response(JSON.stringify({ ok:true }),{status:200,headers:{'Content-Type':'application/json'}}));
    await apiRequest('/api/test',{method:'POST',body:{valeur:'test'},csrf:true});
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0][1]).toMatchObject({credentials:'include'});
    const options=fetchMock.mock.calls[1][1] as RequestInit;
    expect(options.credentials).toBe('include');
    expect(new Headers(options.headers).get('X-XSRF-TOKEN')).toBe('csrf-test');
  });

  it('ne persiste jamais le jeton CSRF dans localStorage', async () => {
    const stockage=vi.spyOn(Storage.prototype,'setItem');
    vi.spyOn(globalThis,'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({headerName:'X-XSRF-TOKEN',parameterName:'_csrf',token:'memoire-seulement'}),{status:200,headers:{'Content-Type':'application/json'}}))
      .mockResolvedValueOnce(new Response(null,{status:204}));
    await apiRequest('/api/auth/logout',{method:'POST',csrf:true});
    expect(stockage).not.toHaveBeenCalled();
  });

  it('envoie un formulaire multipart avec CSRF sans imposer un Content-Type JSON', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'csrf-image' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ imageUrl: '/api/produits/1/image' }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
    const formulaire = new FormData();
    formulaire.append('fichier', new File(['image'], 'produit.png', { type: 'image/png' }));

    await apiRequest('/api/produits/1/image', { method: 'PUT', body: formulaire, csrf: true });

    const options = fetchMock.mock.calls[1][1] as RequestInit;
    expect(options.body).toBe(formulaire);
    expect(new Headers(options.headers).get('Content-Type')).toBeNull();
    expect(new Headers(options.headers).get('X-XSRF-TOKEN')).toBe('csrf-image');
    expect(options.credentials).toBe('include');
  });

  it.each([400, 403, 404, 409, 422, 500])(
    'conserve le statut HTTP %s et le message public du backend',
    async (status) => {
      vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
        code: `ERREUR_${status}`,
        message: `Message public ${status}`,
        validationErrors: status === 400 ? { nom: 'Nom invalide.' } : {},
      }), { status, headers: { 'Content-Type': 'application/json' } }));

      await expect(apiRequest('/api/test-erreur')).rejects.toMatchObject({
        status,
        code: `ERREUR_${status}`,
        message: `Message public ${status}`,
      });
    },
  );

  it('signale l’expiration de session sur une réponse 401', async () => {
    const expiration = vi.fn();
    definirGestionnaireExpirationSession(expiration);
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      code: 'UNAUTHORIZED',
      message: 'Authentification requise.',
    }), { status: 401, headers: { 'Content-Type': 'application/json' } }));

    await expect(apiRequest('/api/protegee')).rejects.toMatchObject({ status: 401 });
    expect(expiration).toHaveBeenCalledTimes(1);
  });

  it('télécharge un PDF avec le cookie de session sans stocker son contenu', async () => {
    const stockage = vi.spyOn(Storage.prototype, 'setItem');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('%PDF-1.7', {
      status: 200,
      headers: { 'Content-Type': 'application/pdf' },
    }));

    const document = await apiBlob('/api/caisse/commandes/CMD-1/facture.pdf');

    expect(document.type).toBe('application/pdf');
    expect(fetchMock).toHaveBeenCalledWith('/api/caisse/commandes/CMD-1/facture.pdf', expect.objectContaining({
      credentials: 'include',
      headers: { Accept: 'application/pdf' },
    }));
    expect(stockage).not.toHaveBeenCalled();
  });
});
