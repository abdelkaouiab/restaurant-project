-- Conserve la trace du réajustement des soldes courants avant d'activer la règle.
INSERT INTO mouvements_stock (
    ingredient_id, utilisateur_id, commande_id, cle_idempotence, type,
    quantite, variation, quantite_apres, motif, cree_le
)
SELECT
    id, NULL, NULL, NULL, 'AJUSTEMENT',
    ABS(ROUND(quantite_disponible) - quantite_disponible),
    ROUND(quantite_disponible) - quantite_disponible,
    ROUND(quantite_disponible),
    'Normalisation automatique : une quantité en unité doit être entière',
    CURRENT_TIMESTAMP
FROM ingredients
WHERE unite = 'UNITE'
  AND quantite_disponible <> TRUNC(quantite_disponible);

UPDATE ingredients
SET quantite_disponible = ROUND(quantite_disponible),
    seuil_alerte = ROUND(seuil_alerte)
WHERE unite = 'UNITE';

UPDATE produit_ingredients pi
SET quantite = GREATEST(1, ROUND(pi.quantite))
FROM ingredients i
WHERE i.id = pi.ingredient_id
  AND i.unite = 'UNITE'
  AND pi.quantite <> TRUNC(pi.quantite);

ALTER TABLE ingredients
    ADD CONSTRAINT ck_ingredients_unite_quantites_entieres CHECK (
        unite <> 'UNITE'
        OR (
            quantite_disponible = TRUNC(quantite_disponible)
            AND seuil_alerte = TRUNC(seuil_alerte)
        )
    );

CREATE FUNCTION verifier_quantites_ingredient_entieres()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    unite_ingredient VARCHAR(20);
    donnees JSONB;
BEGIN
    donnees := TO_JSONB(NEW);
    SELECT unite INTO unite_ingredient FROM ingredients WHERE id = NEW.ingredient_id;
    IF unite_ingredient = 'UNITE' THEN
        IF TG_TABLE_NAME = 'produit_ingredients' AND (donnees ->> 'quantite')::NUMERIC <> TRUNC((donnees ->> 'quantite')::NUMERIC) THEN
            RAISE EXCEPTION 'Une quantité mesurée à l''unité doit être un nombre entier.' USING ERRCODE = '23514';
        ELSIF TG_TABLE_NAME = 'mouvements_stock' AND (
            (donnees ->> 'quantite')::NUMERIC <> TRUNC((donnees ->> 'quantite')::NUMERIC)
            OR (donnees ->> 'variation')::NUMERIC <> TRUNC((donnees ->> 'variation')::NUMERIC)
            OR (donnees ->> 'quantite_apres')::NUMERIC <> TRUNC((donnees ->> 'quantite_apres')::NUMERIC)
        ) THEN
            RAISE EXCEPTION 'Une quantité mesurée à l''unité doit être un nombre entier.' USING ERRCODE = '23514';
        ELSIF TG_TABLE_NAME = 'lignes_achat' AND (
            (donnees ->> 'quantite_commandee')::NUMERIC <> TRUNC((donnees ->> 'quantite_commandee')::NUMERIC)
            OR (donnees ->> 'quantite_recue')::NUMERIC <> TRUNC((donnees ->> 'quantite_recue')::NUMERIC)
        ) THEN
            RAISE EXCEPTION 'Une quantité mesurée à l''unité doit être un nombre entier.' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_recettes_quantites_entieres
BEFORE INSERT OR UPDATE ON produit_ingredients
FOR EACH ROW EXECUTE FUNCTION verifier_quantites_ingredient_entieres();

CREATE TRIGGER trg_mouvements_quantites_entieres
BEFORE INSERT OR UPDATE ON mouvements_stock
FOR EACH ROW EXECUTE FUNCTION verifier_quantites_ingredient_entieres();

CREATE TRIGGER trg_achats_quantites_entieres
BEFORE INSERT OR UPDATE ON lignes_achat
FOR EACH ROW EXECUTE FUNCTION verifier_quantites_ingredient_entieres();

CREATE FUNCTION verifier_reception_quantite_entiere()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    unite_ingredient VARCHAR(20);
BEGIN
    SELECT i.unite INTO unite_ingredient
    FROM lignes_achat la
    JOIN ingredients i ON i.id = la.ingredient_id
    WHERE la.id = NEW.ligne_achat_id;

    IF unite_ingredient = 'UNITE' AND NEW.quantite <> TRUNC(NEW.quantite) THEN
        RAISE EXCEPTION 'Une quantité mesurée à l''unité doit être un nombre entier.' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_receptions_quantites_entieres
BEFORE INSERT OR UPDATE ON lignes_reception_achat
FOR EACH ROW EXECUTE FUNCTION verifier_reception_quantite_entiere();
