ALTER TABLE produits
    ADD COLUMN disponible BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN image_nom_fichier VARCHAR(80),
    ADD COLUMN image_type_mime VARCHAR(20),
    ADD COLUMN image_taille BIGINT;

ALTER TABLE produits
    ADD CONSTRAINT ck_produits_image_complete CHECK (
        (image_nom_fichier IS NULL AND image_type_mime IS NULL AND image_taille IS NULL)
        OR
        (image_nom_fichier IS NOT NULL AND image_type_mime IS NOT NULL AND image_taille IS NOT NULL)
    ),
    ADD CONSTRAINT ck_produits_image_nom_non_vide CHECK (
        image_nom_fichier IS NULL OR char_length(btrim(image_nom_fichier)) > 0
    ),
    ADD CONSTRAINT ck_produits_image_type_autorise CHECK (
        image_type_mime IS NULL OR image_type_mime IN ('image/png', 'image/jpeg', 'image/webp')
    ),
    ADD CONSTRAINT ck_produits_image_taille_positive CHECK (
        image_taille IS NULL OR image_taille > 0
    );

CREATE INDEX idx_produits_disponible ON produits (disponible);
CREATE INDEX idx_produits_actif_disponible ON produits (actif, disponible);
