import type { ConfigurationReferentiel, ModuleReferentielId } from './types';

export const CONFIGURATIONS_REFERENTIELS: Record<ModuleReferentielId, ConfigurationReferentiel> = {
  employes: {
    id: 'employes', endpoint: '/api/employes', titre: 'Employés', singulier: 'employé', article: 'un employé', demonstratif: 'cet', aucun: 'Aucun employé',
    description: 'Consultez les informations professionnelles strictement utiles à la gestion de l’équipe.',
    permissionConsulter: 'EMPLOYE_CONSULTER', permissionGerer: 'EMPLOYE_GERER',
    placeholderRecherche: 'Matricule, nom, prénom ou poste', rechercheMax: 100, triDefaut: 'nom',
    tris: [{ valeur: 'matricule', libelle: 'Matricule' }, { valeur: 'nom', libelle: 'Nom' }, { valeur: 'prenom', libelle: 'Prénom' }, { valeur: 'poste', libelle: 'Poste' }, { valeur: 'creeLe', libelle: 'Date de création' }],
  },
  clients: {
    id: 'clients', endpoint: '/api/clients', titre: 'Clients', singulier: 'client', article: 'un client', demonstratif: 'ce', aucun: 'Aucun client',
    description: 'Gérez les coordonnées nécessaires au suivi des clients.',
    permissionConsulter: 'CLIENT_CONSULTER', permissionGerer: 'CLIENT_GERER',
    placeholderRecherche: 'Nom, prénom, e-mail ou téléphone', rechercheMax: 100, triDefaut: 'nom',
    tris: [{ valeur: 'nom', libelle: 'Nom' }, { valeur: 'prenom', libelle: 'Prénom' }, { valeur: 'email', libelle: 'E-mail' }, { valeur: 'creeLe', libelle: 'Date de création' }],
  },
  tables: {
    id: 'tables', endpoint: '/api/tables', titre: 'Tables', singulier: 'table', article: 'une table', demonstratif: 'cette', aucun: 'Aucune table',
    description: 'Consultez les capacités, la disponibilité, les réservations et les commandes associées aux tables.',
    permissionConsulter: 'TABLE_CONSULTER', permissionGerer: 'TABLE_GERER',
    placeholderRecherche: 'Numéro exact', rechercheMax: 20, triDefaut: 'numero',
    tris: [{ valeur: 'numero', libelle: 'Numéro' }, { valeur: 'capacite', libelle: 'Capacité' }, { valeur: 'creeLe', libelle: 'Date de création' }],
  },
  categories: {
    id: 'categories', endpoint: '/api/categories', titre: 'Catégories', singulier: 'catégorie', article: 'une catégorie', demonstratif: 'cette', aucun: 'Aucune catégorie',
    description: 'Organisez le catalogue avec des catégories actives ou inactives.',
    permissionConsulter: 'CATEGORIE_CONSULTER', permissionGerer: 'CATEGORIE_GERER',
    placeholderRecherche: 'Nom ou description', rechercheMax: 100, triDefaut: 'nom',
    tris: [{ valeur: 'nom', libelle: 'Nom' }, { valeur: 'creeLe', libelle: 'Date de création' }, { valeur: 'modifieLe', libelle: 'Dernière modification' }],
  },
  produits: {
    id: 'produits', endpoint: '/api/produits', titre: 'Produits', singulier: 'produit', article: 'un produit', demonstratif: 'ce', aucun: 'Aucun produit',
    description: 'Gérez le catalogue, les catégories, les prix et la disponibilité des produits.',
    permissionConsulter: 'PRODUIT_CONSULTER', permissionGerer: 'PRODUIT_GERER',
    placeholderRecherche: 'Nom, description ou catégorie', rechercheMax: 100, triDefaut: 'nom',
    tris: [{ valeur: 'nom', libelle: 'Nom' }, { valeur: 'prix', libelle: 'Prix' }, { valeur: 'categorie', libelle: 'Catégorie' }, { valeur: 'disponible', libelle: 'Disponibilité' }, { valeur: 'creeLe', libelle: 'Date de création' }, { valeur: 'modifieLe', libelle: 'Dernière modification' }],
  },
};
