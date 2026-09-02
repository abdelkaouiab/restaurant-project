package com.restaurant.management.repository;

import com.restaurant.management.dto.ConsommationIngredientResponse;
import com.restaurant.management.dto.ConsommationProduitResponse;
import com.restaurant.management.enums.UniteMesure;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class ConsommationRepository {

    private static final String COMMANDES_CONSOMMEES =
            "'CONFIRMEE','EN_PREPARATION','PRETE','PAYEE'";

    private final JdbcTemplate jdbc;

    public ConsommationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ConsommationProduitResponse> produitsConsommes(Instant debut, Instant finExclusive) {
        return jdbc.query("""
                select p.id, p.nom, sum(l.quantite) as quantite
                from lignes_commande l
                join commandes c on c.id = l.commande_id
                join produits p on p.id = l.produit_id
                where c.confirmee_le >= ?
                  and c.confirmee_le < ?
                  and c.statut in (%s)
                group by p.id, p.nom
                order by p.nom asc, p.id asc
                """.formatted(COMMANDES_CONSOMMEES),
                (resultat, ligne) -> new ConsommationProduitResponse(
                        resultat.getLong("id"),
                        resultat.getString("nom"),
                        resultat.getLong("quantite")
                ),
                Timestamp.from(debut),
                Timestamp.from(finExclusive));
    }

    public List<ConsommationIngredientResponse> ingredientsUtilises(Instant debut, Instant finExclusive) {
        return jdbc.query("""
                select i.id, i.nom, i.unite, sum(pi.quantite * l.quantite) as quantite
                from lignes_commande l
                join commandes c on c.id = l.commande_id
                join produit_ingredients pi on pi.produit_id = l.produit_id
                join ingredients i on i.id = pi.ingredient_id
                where c.confirmee_le >= ?
                  and c.confirmee_le < ?
                  and c.statut in (%s)
                group by i.id, i.nom, i.unite
                order by i.nom asc, i.id asc
                """.formatted(COMMANDES_CONSOMMEES),
                (resultat, ligne) -> new ConsommationIngredientResponse(
                        resultat.getLong("id"),
                        resultat.getString("nom"),
                        resultat.getBigDecimal("quantite").setScale(3, RoundingMode.UNNECESSARY),
                        UniteMesure.valueOf(resultat.getString("unite"))
                ),
                Timestamp.from(debut),
                Timestamp.from(finExclusive));
    }
}
