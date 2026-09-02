import { ApiError } from '../../api/client';

export function messageErreur(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return error instanceof TypeError
      ? 'Impossible de joindre le service. Vérifiez votre connexion puis réessayez.'
      : 'L’opération n’a pas pu être effectuée. Réessayez.';
  }
  const message = error.message.trim();
  const contientDetailTechnique = /(exception|stack|trace|sql|select\s|insert\s|update\s|delete\s|constraint|java\.|org\.|nullpointer|endpoint|json|http\s?\d{3})/i.test(message);
  if (message && !contientDetailTechnique) return message;
  switch (error.status) {
    case 400: return 'Certaines informations sont invalides.';
    case 401: return 'Votre connexion a expiré. Veuillez vous reconnecter.';
    case 403: return 'Vous n’avez pas l’autorisation d’effectuer cette action.';
    case 404: return 'L’élément demandé est introuvable.';
    case 409: return 'Cet élément a été modifié ou entre en conflit avec une autre opération.';
    case 422: return 'Certaines informations ne respectent pas les règles attendues.';
    default: return error.status >= 500
      ? 'Le service est temporairement indisponible.'
      : 'L’opération n’a pas pu être effectuée. Réessayez.';
  }
}

export function erreursValidation(error: unknown): Record<string, string> {
  return error instanceof ApiError ? error.validationErrors : {};
}
