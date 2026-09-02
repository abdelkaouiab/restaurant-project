# Fournisseurs et achats — étape 11

## Modèle relationnel

```text
fournisseurs 1 ─── * achats 1 ─── * lignes_achat * ─── 1 ingredients
                       │
                       └── 1 ─── * receptions_achat 1 ─── * lignes_reception_achat
                                      │
                                      └── 1 ─── * mouvements_stock
```

`Achat` et `LigneAchat` utilisent `@Version`. Les quantités commandées, reçues
et restantes sont exprimées dans l'unité de l'ingrédient. Elles sont
obligatoirement entières lorsque cette unité est `UNITE`. Le prix unitaire est
copié dans la ligne et le sous-total ainsi que le total sont toujours calculés
côté serveur avec `BigDecimal`.

## Cycle de vie

Transitions autorisées :

```text
BROUILLON ── commander ──> COMMANDE ── réception partielle ──> PARTIELLEMENT_RECU
    │                         │                                      │
    └── annuler ──> ANNULE    ├── réception complète ──> RECU       └── réception restante ──> RECU
                              └── annuler ──> ANNULE
```

Un brouillon doit contenir au moins une ligne avant d'être commandé. Une ligne
ne peut être ajoutée qu'au brouillon. Un achat partiellement ou complètement
reçu ne peut plus être annulé ni modifié arbitrairement.

## Réception et stock

Une réception est une opération transactionnelle :

1. verrou pessimiste sur l'achat et lecture de ses lignes ;
2. contrôle atomique du reliquat de chaque ligne ;
3. persistance de la réception et de ses quantités ;
4. verrou des ingrédients dans l'ordre croissant des identifiants ;
5. création d'un mouvement `ENTREE` et augmentation du stock correspondant ;
6. mise à jour du statut de l'achat et journalisation.

La clé UUID fournie dans `Idempotency-Key` est unique en base. Une nouvelle
requête avec la même clé et le même contenu retourne l'achat sans créer un
second mouvement. La réutilisation de la clé avec un autre achat ou d'autres
quantités est refusée. La contrainte unique
`(reception_achat_id, ingredient_id)` constitue une seconde protection.

L'historique détaillé est consultable par achat. Il expose la date et les
quantités de chaque réception, sans révéler la clé d'idempotence ni sa
signature interne.

## Autorisations

- `ACHAT_CONSULTER` : consulter fournisseurs et achats ;
- `ACHAT_GERER` : créer/modifier les fournisseurs, gérer les brouillons,
  commander, recevoir et annuler.

La désactivation d'un fournisseur empêche uniquement la création de nouveaux
achats avec celui-ci. Les achats et réceptions existants restent consultables
et peuvent poursuivre leur cycle autorisé. La transition de statut exige la
version courante du fournisseur afin de détecter une modification concurrente.

Ces permissions sont attribuées à `ADMINISTRATEUR`, `GERANT` et
`RESPONSABLE_STOCK`.

## Routes

| Méthode | Route | Effet |
|---|---|---|
| `POST` | `/api/achats/fournisseurs` | créer un fournisseur |
| `PUT` | `/api/achats/fournisseurs/{id}` | modifier un fournisseur |
| `PATCH` | `/api/achats/fournisseurs/{id}/statut` | activer ou désactiver avec contrôle de version |
| `GET` | `/api/achats/fournisseurs` | rechercher et paginer |
| `POST` | `/api/achats` | créer un brouillon |
| `POST` | `/api/achats/{id}/lignes` | ajouter une ligne au brouillon |
| `POST` | `/api/achats/{id}/commande` | commander |
| `POST` | `/api/achats/{id}/receptions` | recevoir avec `Idempotency-Key` |
| `GET` | `/api/achats/{id}/receptions` | consulter les réceptions individuelles |
| `POST` | `/api/achats/{id}/annulation` | annuler |
| `GET` | `/api/achats/{id}` | consulter un achat |
| `GET` | `/api/achats` | rechercher et paginer |

Les champs de tri sont limités à une liste blanche.
