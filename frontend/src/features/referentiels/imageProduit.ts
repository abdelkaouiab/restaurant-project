const TYPES_IMAGE_PRODUIT = new Set(['image/png', 'image/jpeg', 'image/webp']);

export const ACCEPT_IMAGE_PRODUIT = 'image/png,image/jpeg,image/webp';
export const TAILLE_MAX_IMAGE_PRODUIT = 5 * 1024 * 1024;

export function erreurValidationImageProduit(fichier: File): string {
  if (!TYPES_IMAGE_PRODUIT.has(fichier.type)) {
    return 'Choisissez une image PNG, JPEG ou WebP.';
  }
  if (fichier.size > TAILLE_MAX_IMAGE_PRODUIT) {
    return 'L’image ne doit pas dépasser 5 Mo.';
  }
  return '';
}
