# Commandes — étape 6

## Modèle

```mermaid
erDiagram
    UTILISATEURS ||--o{ COMMANDES : "crée"
    TABLES_RESTAURANT ||--o{ COMMANDES : "reçoit"
    CLIENTS o|--o{ COMMANDES : "peut concerner"
    COMMANDES ||--|{ LIGNES_COMMANDE : "contient"
    PRODUITS ||--o{ LIGNES_COMMANDE : "est copié dans"

    COMMANDES {
        bigint id PK
        varchar numero UK
        bigint table_id FK
        bigint client_id FK
        bigint cree_par_id FK
        varchar statut
        numeric montant_total
        bigint version
    }
    LIGNES_COMMANDE {
        bigint id PK
        bigint commande_id FK
        bigint produit_id FK
        integer quantite
        numeric prix_unitaire
        numeric sous_total
        bigint version
    }
```

## Transitions

```text
BROUILLON -> CONFIRMEE -> ANNULEE
    |
    +------------------> ANNULEE
```

Une commande confirmée ne peut plus changer de table, de client ou de lignes.

## Routes

- `POST /api/commandes` : créer un brouillon ;
- `PUT /api/commandes/{id}/association` : changer table/client du brouillon ;
- `POST /api/commandes/{id}/lignes` : ajouter un produit ;
- `PUT /api/commandes/{id}/lignes/{ligneId}` : changer une quantité ;
- `DELETE /api/commandes/{id}/lignes/{ligneId}` : retirer une ligne du brouillon ;
- `POST /api/commandes/{id}/confirmation` : confirmer ;
- `POST /api/commandes/{id}/annulation` : annuler ;
- `GET /api/commandes/{id}` et `GET /api/commandes` : consulter.

Toutes les écritures nécessitent CSRF et `COMMANDE_GERER`. Les lectures nécessitent
`COMMANDE_CONSULTER`.
