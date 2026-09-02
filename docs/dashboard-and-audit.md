# Tableau de bord et audit — étape 12

## API

| Route | Autorisation | Contenu |
|---|---|---|
| `GET /api/tableau-de-bord/operations` | `TABLEAU_BORD_CONSULTER` | commandes, états, produits vendus, réservations, tables disponibles, stocks faibles |
| `GET /api/tableau-de-bord/finances` | rôle `GERANT` ou `ADMINISTRATEUR` et permission financière | chiffre d'affaires, paiements par mode, dépenses d'achat |
| `GET /api/tableau-de-bord/journal` | `JOURNAL_ACTIVITE_CONSULTER` | journal paginé et filtré |

Toutes les routes sont en lecture seule. Aucune route de modification ou de
suppression des journaux n'est exposée.

## Périodes

`debut` et `fin` utilisent le format ISO `YYYY-MM-DD` et sont inclusives. Le
serveur les transforme en intervalle UTC semi-ouvert
`[debut 00:00, lendemain(fin) 00:00)`. La période par défaut couvre les trente
derniers jours et une requête ne peut pas dépasser 366 jours.

## Définitions des indicateurs

- chiffre d'affaires : somme des paiements `VALIDE` encaissés pendant la période ;
- nombre et états des commandes : commandes créées pendant la période ;
- produits les plus vendus : quantités des lignes de commandes non brouillon et non annulées, limitées aux dix premières ;
- paiements par mode : somme des paiements validés par mode ;
- réservations : nombre de réservations par état selon leur date de début ;
- tables disponibles : photographie à l'instant de la requête des tables actives sans commande en cours et sans réservation en cours ;
- stocks faibles : photographie des ingrédients actifs dont le disponible est inférieur ou égal au seuil, limitée à cent lignes ;
- dépenses d'achat : total des achats non annulés commandés pendant la période.

Les agrégats sont exécutés directement par PostgreSQL avec des requêtes
groupées et des index dédiés. Les API ne parcourent pas de graphes JPA et
n'introduisent donc pas de requêtes N+1.

## Journal sécurisé

Le journal peut être filtré par période, action, type de ressource, résultat et
identifiant d'utilisateur. Le tri est limité à `creeLe`, `action`,
`typeRessource` et `resultat`. Le DTO public exclut volontairement les champs
libres `details` et `adresseIp`, afin de ne jamais renvoyer un secret qui aurait
été journalisé par erreur.
