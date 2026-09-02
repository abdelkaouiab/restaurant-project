import { describe, expect, it } from 'vitest';
import { ApiError } from '../../api/client';
import { messageErreur } from './erreurs';

describe('messages d’erreur frontend', () => {
  it.each([
    [400, 'Certaines informations sont invalides.'],
    [401, 'Votre connexion a expiré. Veuillez vous reconnecter.'],
    [403, 'Vous n’avez pas l’autorisation d’effectuer cette action.'],
    [404, 'L’élément demandé est introuvable.'],
    [409, 'Cet élément a été modifié ou entre en conflit avec une autre opération.'],
    [422, 'Certaines informations ne respectent pas les règles attendues.'],
    [500, 'Le service est temporairement indisponible.'],
  ])('fournit un repli sûr pour le statut %s', (status, attendu) => {
    expect(messageErreur(new ApiError(status as number, 'TEST', ''))).toBe(attendu);
  });

  it('identifie une erreur réseau sans divulguer de détail technique', () => {
    expect(messageErreur(new TypeError('Failed to fetch')))
      .toBe('Impossible de joindre le service. Vérifiez votre connexion puis réessayez.');
  });

  it('masque les détails techniques contenus dans un message', () => {
    expect(messageErreur(new ApiError(500, 'INTERNAL_ERROR', 'org.hibernate.SQLGrammarException: select * from utilisateurs')))
      .toBe('Le service est temporairement indisponible.');
  });
});
