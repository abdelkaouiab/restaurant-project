INSERT INTO permissions (nom, description) VALUES
 ('TABLEAU_BORD_CONSULTER', 'Consulter les indicateurs opérationnels'),
 ('TABLEAU_BORD_FINANCIER_CONSULTER', 'Consulter les indicateurs financiers');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.nom IN ('ADMINISTRATEUR','GERANT','SERVEUR','CUISINE','CAISSIER','RESPONSABLE_STOCK')
  AND p.nom = 'TABLEAU_BORD_CONSULTER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.nom IN ('ADMINISTRATEUR','GERANT')
  AND p.nom = 'TABLEAU_BORD_FINANCIER_CONSULTER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.nom = 'GERANT' AND p.nom = 'JOURNAL_ACTIVITE_CONSULTER'
ON CONFLICT DO NOTHING;

CREATE INDEX idx_commandes_cree_statut ON commandes (cree_le, statut);
CREATE INDEX idx_paiements_cree_statut_mode ON paiements (cree_le, statut, mode);
CREATE INDEX idx_reservations_debut_statut ON reservations (debut, statut);
CREATE INDEX idx_achats_commande_statut ON achats (commande_le, statut) WHERE commande_le IS NOT NULL;
CREATE INDEX idx_journaux_date_action ON journaux_activite (cree_le DESC, action);
CREATE INDEX idx_lignes_commande_produit ON lignes_commande (produit_id, commande_id);
