export interface OptionJournal {
  valeur: string;
  libelle: string;
}

export const ACTIONS_JOURNAL: readonly OptionJournal[] = [
  { valeur: 'CONNEXION', libelle: 'Connexion' },
  { valeur: 'DECONNEXION', libelle: 'Déconnexion' },
  { valeur: 'UTILISATEUR_CREATION', libelle: 'Création d’un utilisateur' },
  { valeur: 'UTILISATEUR_MODIFICATION', libelle: 'Modification d’un utilisateur' },
  { valeur: 'UTILISATEUR_ROLES_MODIFICATION', libelle: 'Modification des rôles' },
  { valeur: 'UTILISATEUR_REACTIVATION', libelle: 'Réactivation d’un utilisateur' },
  { valeur: 'UTILISATEUR_DESACTIVATION', libelle: 'Désactivation d’un utilisateur' },
  { valeur: 'UTILISATEUR_MOT_DE_PASSE_REINITIALISATION', libelle: 'Réinitialisation d’un mot de passe' },
  { valeur: 'EMPLOYE_DESACTIVATION', libelle: 'Désactivation d’un employé' },
  { valeur: 'CLIENT_DESACTIVATION', libelle: 'Désactivation d’un client' },
  { valeur: 'TABLE_DESACTIVATION', libelle: 'Désactivation d’une table' },
  { valeur: 'CATEGORIE_DESACTIVATION', libelle: 'Désactivation d’une catégorie' },
  { valeur: 'PRODUIT_PRIX_MODIFICATION', libelle: 'Modification du prix d’un produit' },
  { valeur: 'PRODUIT_IMAGE_MODIFICATION', libelle: 'Ajout ou modification d’une image produit' },
  { valeur: 'PRODUIT_IMAGE_SUPPRESSION', libelle: 'Suppression d’une image produit' },
  { valeur: 'PRODUIT_DESACTIVATION', libelle: 'Désactivation d’un produit' },
  { valeur: 'COMMANDE_CREATION', libelle: 'Création d’une commande' },
  { valeur: 'COMMANDE_CONFIRMATION', libelle: 'Confirmation d’une commande' },
  { valeur: 'COMMANDE_ANNULATION', libelle: 'Annulation d’une commande' },
  { valeur: 'CUISINE_PREPARATION_DEMARREE', libelle: 'Démarrage d’une préparation' },
  { valeur: 'CUISINE_COMMANDE_PRETE', libelle: 'Commande déclarée prête' },
  { valeur: 'PAIEMENT_VALIDE', libelle: 'Validation d’un paiement' },
  { valeur: 'FACTURE_EMISE', libelle: 'Émission d’une facture' },
  { valeur: 'RESERVATION_CREATION', libelle: 'Création d’une réservation' },
  { valeur: 'RESERVATION_CONFIRMATION', libelle: 'Confirmation d’une réservation' },
  { valeur: 'RESERVATION_ARRIVEE', libelle: 'Arrivée d’une réservation' },
  { valeur: 'RESERVATION_TERMINAISON', libelle: 'Terminaison d’une réservation' },
  { valeur: 'RESERVATION_ANNULATION', libelle: 'Annulation d’une réservation' },
  { valeur: 'RESERVATION_ABSENCE', libelle: 'Absence à une réservation' },
  { valeur: 'RESERVATION_COMMANDE_CREATION', libelle: 'Commande créée depuis une réservation' },
  { valeur: 'STOCK_ENTREE', libelle: 'Entrée de stock' },
  { valeur: 'STOCK_SORTIE', libelle: 'Sortie de stock' },
  { valeur: 'STOCK_AJUSTEMENT', libelle: 'Ajustement de stock' },
  { valeur: 'STOCK_PERTE', libelle: 'Perte de stock' },
  { valeur: 'STOCK_RETOUR', libelle: 'Retour en stock' },
  { valeur: 'STOCK_DEDUCTION_COMMANDE', libelle: 'Déduction du stock par une commande' },
  { valeur: 'STOCK_RECETTE_INGREDIENT_SUPPRESSION', libelle: 'Retrait d’un ingrédient d’une recette' },
  { valeur: 'FOURNISSEUR_REACTIVATION', libelle: 'Réactivation d’un fournisseur' },
  { valeur: 'FOURNISSEUR_DESACTIVATION', libelle: 'Désactivation d’un fournisseur' },
  { valeur: 'ACHAT_CREATION', libelle: 'Création d’un achat' },
  { valeur: 'ACHAT_COMMANDE', libelle: 'Commande d’un achat' },
  { valeur: 'ACHAT_ANNULATION', libelle: 'Annulation d’un achat' },
  { valeur: 'ACHAT_RECEPTION', libelle: 'Réception d’un achat' },
];

export const RESSOURCES_JOURNAL: readonly OptionJournal[] = [
  { valeur: 'AUTHENTIFICATION', libelle: 'Authentification' },
  { valeur: 'UTILISATEUR', libelle: 'Utilisateur' },
  { valeur: 'EMPLOYE', libelle: 'Employé' },
  { valeur: 'CLIENT', libelle: 'Client' },
  { valeur: 'TABLE_RESTAURANT', libelle: 'Table du restaurant' },
  { valeur: 'CATEGORIE', libelle: 'Catégorie' },
  { valeur: 'PRODUIT', libelle: 'Produit' },
  { valeur: 'COMMANDE', libelle: 'Commande' },
  { valeur: 'PAIEMENT', libelle: 'Paiement' },
  { valeur: 'FACTURE', libelle: 'Facture' },
  { valeur: 'RESERVATION', libelle: 'Réservation' },
  { valeur: 'MOUVEMENT_STOCK', libelle: 'Mouvement de stock' },
  { valeur: 'PRODUIT_INGREDIENT', libelle: 'Association produit-ingrédient' },
  { valeur: 'FOURNISSEUR', libelle: 'Fournisseur' },
  { valeur: 'ACHAT', libelle: 'Achat' },
  { valeur: 'RECEPTION_ACHAT', libelle: 'Réception d’achat' },
];

export function libelleActionJournal(valeur: string): string {
  return ACTIONS_JOURNAL.find((option) => option.valeur === valeur)?.libelle ?? 'Autre opération';
}

export function libelleRessourceJournal(valeur: string): string {
  return RESSOURCES_JOURNAL.find((option) => option.valeur === valeur)?.libelle ?? 'Autre élément';
}
