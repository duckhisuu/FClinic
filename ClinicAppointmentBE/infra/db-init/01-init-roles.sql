DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'fclinic_admin') THEN
        CREATE ROLE fclinic_admin WITH LOGIN SUPERUSER PASSWORD 'fclinic_secret';
    ELSE
        ALTER ROLE fclinic_admin WITH LOGIN SUPERUSER PASSWORD 'fclinic_secret';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'simpi') THEN
        CREATE ROLE simpi WITH LOGIN SUPERUSER PASSWORD '1231';
    ELSE
        ALTER ROLE simpi WITH LOGIN SUPERUSER PASSWORD '1231';
    END IF;
END $$;
