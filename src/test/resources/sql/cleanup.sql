TRUNCATE autoleasing_core.app_users, autoleasing_core.payments, autoleasing_core.contracts,
         autoleasing_core.clients, autoleasing_core.cars RESTART IDENTITY CASCADE;
TRUNCATE autoleasing_core_history.payments_aud, autoleasing_core_history.contracts_aud,
         autoleasing_core_history.clients_aud, autoleasing_core_history.cars_aud,
         autoleasing_core_history.revinfo CASCADE;
