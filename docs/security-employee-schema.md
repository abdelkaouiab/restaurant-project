# Schéma sécurité et employés — étape 2

## Schéma relationnel

```mermaid
erDiagram
    EMPLOYES ||--o| UTILISATEURS : "dispose éventuellement d'un compte"
    UTILISATEURS ||--o{ UTILISATEUR_ROLES : "reçoit"
    ROLES ||--o{ UTILISATEUR_ROLES : "est attribué"
    ROLES ||--o{ ROLE_PERMISSIONS : "accorde"
    PERMISSIONS ||--o{ ROLE_PERMISSIONS : "est accordée"
    UTILISATEURS o|--o{ JOURNAUX_ACTIVITE : "agit dans"

    EMPLOYES {
        bigint id PK
        varchar matricule UK
        varchar nom
        varchar prenom
        varchar telephone
        varchar poste
        date date_embauche
        boolean actif
        timestamptz cree_le
        timestamptz modifie_le
        bigint version
    }

    UTILISATEURS {
        bigint id PK
        bigint employe_id FK,UK
        varchar email UK
        varchar mot_de_passe_hash
        boolean actif
        timestamptz cree_le
        timestamptz modifie_le
        bigint version
    }

    ROLES {
        bigint id PK
        varchar nom UK
        varchar description
        boolean actif
        timestamptz cree_le
        timestamptz modifie_le
        bigint version
    }

    PERMISSIONS {
        bigint id PK
        varchar nom UK
        varchar description
        boolean actif
        timestamptz cree_le
        timestamptz modifie_le
        bigint version
    }

    UTILISATEUR_ROLES {
        bigint utilisateur_id PK,FK
        bigint role_id PK,FK
    }

    ROLE_PERMISSIONS {
        bigint role_id PK,FK
        bigint permission_id PK,FK
    }

    JOURNAUX_ACTIVITE {
        bigint id PK
        bigint utilisateur_id FK
        varchar action
        varchar type_ressource
        varchar ressource_id
        varchar resultat
        varchar details
        varchar adresse_ip
        uuid correlation_id
        timestamptz cree_le
    }
```

## Invariants

- L'e-mail est stocké en minuscules sans espaces périphériques et son index unique est insensible à la casse.
- Un employé possède au maximum un compte ; un compte référence obligatoirement un employé.
- Les noms des rôles et permissions sont uniques et correspondent aux enums Java.
- Les agrégats mutables (`Employe`, `Utilisateur`, `Role`, `Permission`) portent `actif`, les dates de création/modification et une version de verrouillage optimiste.
- Le journal d'activité est append-only. Son acteur est nullable pour les événements système et devient `NULL` si un compte devait être supprimé, afin de préserver le journal.
- Les tables d'association ont une clé primaire composée empêchant les doublons.
- Les champs texte obligatoires sont contrôlés comme non vides et toutes les tailles maximales sont imposées par PostgreSQL.

## Référentiels initiaux

La migration `V2` insère les six rôles prévus et sept permissions de gestion des utilisateurs, employés, rôles et journaux. Elle associe toutes les permissions à `ADMINISTRATEUR`, un sous-ensemble à `GERANT` et la consultation des employés aux rôles opérationnels.

La migration ne crée aucun employé, aucun utilisateur et aucune donnée de mot de passe.
