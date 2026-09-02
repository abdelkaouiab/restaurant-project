export const MESSAGE_QUANTITE_UNITAIRE = 'Une quantité mesurée à l’unité doit être un nombre entier.';

export function quantiteCompatibleAvecUnite(valeur: number, unite: string | undefined): boolean {
  return unite !== 'UNITE' || Number.isInteger(valeur);
}

export function formatQuantite(valeur: number, unite: string | undefined): string {
  const decimales = unite === 'UNITE' ? 0 : 3;
  return Number(valeur).toLocaleString('fr-FR', {
    minimumFractionDigits: decimales,
    maximumFractionDigits: decimales,
  });
}

export function pasQuantite(unite: string | undefined): number {
  return unite === 'UNITE' ? 1 : 0.001;
}
