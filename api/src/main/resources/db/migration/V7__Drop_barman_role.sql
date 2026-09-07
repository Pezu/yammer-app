-- The BARMAN role is retired: remove it from the catalog and from any user carrying it.
UPDATE users SET roles = array_remove(roles, 'BARMAN') WHERE 'BARMAN' = ANY(roles);
DELETE FROM role WHERE role = 'BARMAN';
