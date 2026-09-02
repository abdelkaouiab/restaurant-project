package com.restaurant.management.repository;

import com.restaurant.management.dto.*;
import com.restaurant.management.enums.UniteMesure;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class TableauBordRepository {
 private final JdbcTemplate jdbc; public TableauBordRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public long nombreCommandes(Instant debut,Instant fin){Long n=jdbc.queryForObject("select count(*) from commandes where cree_le>=? and cree_le<?",Long.class,Timestamp.from(debut),Timestamp.from(fin));return n==null?0:n;}
 public List<IndicateurValeurResponse> commandesParEtat(Instant debut,Instant fin){return jdbc.query("select statut,count(*) from commandes where cree_le>=? and cree_le<? group by statut order by statut",(rs,n)->new IndicateurValeurResponse(rs.getString(1),BigDecimal.valueOf(rs.getLong(2))),Timestamp.from(debut),Timestamp.from(fin));}
 public List<ProduitVenduResponse> produitsVendus(Instant debut,Instant fin){return jdbc.query("""
   select p.id,p.nom,sum(l.quantite) quantite from lignes_commande l
   join commandes c on c.id=l.commande_id join produits p on p.id=l.produit_id
   where c.cree_le>=? and c.cree_le<? and c.statut not in ('BROUILLON','ANNULEE')
   group by p.id,p.nom order by quantite desc,p.nom asc limit 10
   """,(rs,n)->new ProduitVenduResponse(rs.getLong(1),rs.getString(2),rs.getLong(3)),Timestamp.from(debut),Timestamp.from(fin));}
 public List<IndicateurValeurResponse> reservations(Instant debut,Instant fin){return jdbc.query("select statut,count(*) from reservations where debut>=? and debut<? group by statut order by statut",(rs,n)->new IndicateurValeurResponse(rs.getString(1),BigDecimal.valueOf(rs.getLong(2))),Timestamp.from(debut),Timestamp.from(fin));}
 public List<StockFaibleDashboardResponse> stocksFaibles(){return jdbc.query("""
   select id,nom,unite,quantite_disponible,seuil_alerte from ingredients
   where actif=true and quantite_disponible<=seuil_alerte order by quantite_disponible-seuil_alerte,nom limit 100
   """,(rs,n)->new StockFaibleDashboardResponse(rs.getLong(1),rs.getString(2),UniteMesure.valueOf(rs.getString(3)),rs.getBigDecimal(4),rs.getBigDecimal(5)));}
 public BigDecimal chiffreAffaires(Instant debut,Instant fin){return montant("select coalesce(sum(montant),0) from paiements where statut='VALIDE' and cree_le>=? and cree_le<?",debut,fin);}
 public List<IndicateurValeurResponse> paiementsParMode(Instant debut,Instant fin){return jdbc.query("select mode,coalesce(sum(montant),0) from paiements where statut='VALIDE' and cree_le>=? and cree_le<? group by mode order by mode",(rs,n)->new IndicateurValeurResponse(rs.getString(1),rs.getBigDecimal(2)),Timestamp.from(debut),Timestamp.from(fin));}
 public BigDecimal depensesAchat(Instant debut,Instant fin){return montant("select coalesce(sum(montant_total),0) from achats where commande_le>=? and commande_le<? and statut<>'ANNULE'",debut,fin);}
 private BigDecimal montant(String sql,Instant debut,Instant fin){BigDecimal v=jdbc.queryForObject(sql,BigDecimal.class,Timestamp.from(debut),Timestamp.from(fin));return v==null?BigDecimal.ZERO.setScale(2):v.setScale(2);}
}
