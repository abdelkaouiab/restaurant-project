INSERT INTO permissions (nom, description)
VALUES
    ('UTILISATEUR_CONSULTER', 'Consulter les comptes utilisateurs'),
    ('UTILISATEUR_GERER', 'Créer, modifier et désactiver les comptes utilisateurs'),
    ('EMPLOYE_CONSULTER', 'Consulter les employés'),
    ('EMPLOYE_GERER', 'Créer, modifier et désactiver les employés'),
    ('ROLE_CONSULTER', 'Consulter les rôles et leurs permissions'),
    ('ROLE_GERER', 'Modifier les attributions de rôles et de permissions'),
    ('JOURNAL_ACTIVITE_CONSULTER', 'Consulter le journal des activités');

INSERT INTO roles (nom, description)
VALUES
    ('ADMINISTRATEUR', 'Administration technique et fonctionnelle complète'),
    ('GERANT', 'Gestion opérationnelle du restaurant'),
    ('SERVEUR', 'Personnel de service en salle'),
    ('CAISSIER', 'Personnel chargé des encaissements'),
    ('CUISINE', 'Personnel chargé de la préparation'),
    ('RESPONSABLE_STOCK', 'Personnel chargé des stocks et approvisionnements');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.nom = 'ADMINISTRATEUR';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.nom IN (
    'UTILISATEUR_CONSULTER',
    'UTILISATEUR_GERER',
    'EMPLOYE_CONSULTER',
    'EMPLOYE_GERER',
    'ROLE_CONSULTER',
    'JOURNAL_ACTIVITE_CONSULTER'
)
WHERE r.nom = 'GERANT';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.nom = 'EMPLOYE_CONSULTER'
WHERE r.nom IN ('SERVEUR', 'CAISSIER', 'CUISINE', 'RESPONSABLE_STOCK');
