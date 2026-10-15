ALTER TABLE autoleasing_core.app_users
    DROP CONSTRAINT app_users_client_id_fkey;

ALTER TABLE autoleasing_core.app_users
    ADD CONSTRAINT app_users_client_id_fkey
        FOREIGN KEY (client_id) REFERENCES autoleasing_core.clients (id) ON DELETE CASCADE;
