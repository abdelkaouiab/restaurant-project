# Étape 8 — Paiements et factures

## Règles métier

- Seules les commandes `PRETE` sont présentées à la caisse et acceptent des
  paiements. Ce choix empêche un paiement complet de retirer prématurément une
  commande du flux cuisine.
- Un paiement est positif, comporte au plus deux décimales et ne peut jamais
  dépasser le montant restant. Le rendu de monnaie n’est pas géré : aucun
  surpaiement n’est accepté.
- Le montant payé est la somme des paiements `VALIDE` persistés. Le montant
  restant est toujours recalculé par le serveur.
- Plusieurs paiements et modes de paiement peuvent couvrir une commande.
- La commande passe à `PAYEE` uniquement quand la somme validée égale son total.
- Une facture unique est créée automatiquement dans la même transaction que le
  dernier paiement. Elle n’existe jamais avant le paiement complet.
- Une facture est un instantané immuable du numéro de commande et de son total.

## Idempotence et concurrence

`POST /api/caisse/commandes/{numero}/paiements` exige l’en-tête UUID
`Idempotency-Key`.

- même clé, même commande, même montant et même mode : la réponse initiale est
  retournée sans nouveau débit ;
- même clé avec un autre contenu : conflit `409` ;
- une contrainte unique garantit l’idempotence en base ;
- un verrou pessimiste sérialise le recalcul et l’écriture des paiements d’une
  même commande ;
- `Commande`, `Paiement` et `Facture` conservent aussi un champ `@Version`.

## Autorisation et confidentialité

Les routes exigent `CAISSE_PAIEMENTS_GERER`, attribuée aux rôles `CAISSIER`,
`GERANT` et `ADMINISTRATEUR`. Les écritures restent protégées par CSRF.

Seuls le montant, le mode de paiement et une clé technique d’idempotence sont
stockés. Aucun numéro de carte, cryptogramme, jeton bancaire ou autre donnée
bancaire sensible n’est accepté ni journalisé.

L’historique de consultation des paiements utilise un DTO dédié qui n’expose
pas la clé d’idempotence. Les listes sont paginées, triées côté serveur par date
décroissante et limitées à 50 éléments par page.

## Routes

- `GET /api/caisse/commandes-a-payer`
- `GET /api/caisse/paiements?date=YYYY-MM-DD&page=0&taille=5`
- `GET /api/caisse/factures?page=0&taille=5`
- `POST /api/caisse/commandes/{numero}/paiements`
- `GET /api/caisse/commandes/{numero}/paiements?page=0&taille=20`
- `GET /api/caisse/commandes/{numero}/facture`
- `GET /api/caisse/commandes/{numero}/facture.pdf`

Sans paramètre `date`, la consultation des paiements utilise la journée UTC en
cours. La liste des factures renvoie les émissions les plus récentes.
La route PDF renvoie une pièce jointe `application/pdf` après paiement complet.
Elle contient uniquement les références de la facture et de la commande, la
table, les produits, les quantités et les montants calculés par le serveur ;
elle ne contient aucune donnée bancaire sensible.

## Schéma

```mermaid
erDiagram
    COMMANDES ||--o{ PAIEMENTS : recoit
    COMMANDES ||--o| FACTURES : genere
    UTILISATEURS ||--o{ PAIEMENTS : encaisse

    PAIEMENTS {
        bigint id PK
        bigint commande_id FK
        bigint encaisse_par_id FK
        uuid cle_idempotence UK
        numeric montant
        varchar mode
        varchar statut
        timestamptz cree_le
        bigint version
    }
    FACTURES {
        bigint id PK
        bigint commande_id FK_UK
        varchar numero UK
        numeric montant_total
        timestamptz emise_le
        bigint version
    }
```
