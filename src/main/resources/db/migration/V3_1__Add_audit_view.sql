CREATE VIEW autoleasing_core_history.v_cars_audit AS
SELECT
    c.rev,
    r.revtstmp,
    r.client_ip,
    CASE c.revtype
        WHEN 0 THEN 'INSERT'
        WHEN 1 THEN 'UPDATE'
        WHEN 2 THEN 'DELETE'
        ELSE 'UNKNOWN'
    END AS operation_type,
    c.vin,
    c.brand,
    c.model,
    c.release_year,
    c.price,
    c.status
FROM autoleasing_core_history.cars_aud c
JOIN autoleasing_core_history.revinfo r ON c.rev = r.rev
ORDER BY c.rev DESC;

CREATE VIEW autoleasing_core_history.v_clients_audit AS
SELECT
    c.rev,
    r.revtstmp,
    r.client_ip,
    CASE c.revtype
        WHEN 0 THEN 'INSERT'
        WHEN 1 THEN 'UPDATE'
        WHEN 2 THEN 'DELETE'
        ELSE 'UNKNOWN'
    END AS operation_type,
    c.id,
    c.name,
    c.passport,
    c.phone
FROM autoleasing_core_history.clients_aud c
JOIN autoleasing_core_history.revinfo r ON c.rev = r.rev
ORDER BY c.rev DESC;

CREATE VIEW autoleasing_core_history.v_contracts_audit AS
SELECT
    c.rev,
    r.revtstmp,
    r.client_ip,
    CASE c.revtype
        WHEN 0 THEN 'INSERT'
        WHEN 1 THEN 'UPDATE'
        WHEN 2 THEN 'DELETE'
        ELSE 'UNKNOWN'
    END AS operation_type,
    c.id,
    c.car_vin,
    c.client_id,
    c.duration_in_months,
    c.down_payment,
    c.interest_rate,
    c.monthly_payment,
    c.status
FROM autoleasing_core_history.contracts_aud c
JOIN autoleasing_core_history.revinfo r ON c.rev = r.rev
ORDER BY c.rev DESC;

CREATE VIEW autoleasing_core_history.v_payments_audit AS
SELECT
    p.rev,
    r.revtstmp,
    r.client_ip,
    CASE p.revtype
        WHEN 0 THEN 'INSERT'
        WHEN 1 THEN 'UPDATE'
        WHEN 2 THEN 'DELETE'
        ELSE 'UNKNOWN'
    END AS operation_type,
    p.payment_id,
    p.contract_id,
    p.amount,
    p.status
FROM autoleasing_core_history.payments_aud p
JOIN autoleasing_core_history.revinfo r ON p.rev = r.rev
ORDER BY p.rev DESC;

CREATE VIEW autoleasing_core_history.v_all_audit_events AS
SELECT
    r.rev,
    r.revtstmp,
    r.client_ip,
    'CARS' AS entity_type,
    CASE c.revtype
        WHEN 0 THEN 'INSERT'
        WHEN 1 THEN 'UPDATE'
        WHEN 2 THEN 'DELETE'
        ELSE 'UNKNOWN'
    END AS operation_type,
    c.vin AS entity_id
FROM autoleasing_core_history.cars_aud c
JOIN autoleasing_core_history.revinfo r ON c.rev = r.rev

UNION ALL

SELECT
    r.rev,
    r.revtstmp,
    r.client_ip,
    'CLIENTS' AS entity_type,
    CASE cl.revtype
        WHEN 0 THEN 'INSERT'
        WHEN 1 THEN 'UPDATE'
        WHEN 2 THEN 'DELETE'
        ELSE 'UNKNOWN'
    END AS operation_type,
    cl.id::VARCHAR AS entity_id
FROM autoleasing_core_history.clients_aud cl
JOIN autoleasing_core_history.revinfo r ON cl.rev = r.rev

UNION ALL

SELECT
    r.rev,
    r.revtstmp,
    r.client_ip,
    'CONTRACTS' AS entity_type,
    CASE co.revtype
        WHEN 0 THEN 'INSERT'
        WHEN 1 THEN 'UPDATE'
        WHEN 2 THEN 'DELETE'
        ELSE 'UNKNOWN'
    END AS operation_type,
    co.id::VARCHAR AS entity_id
FROM autoleasing_core_history.contracts_aud co
JOIN autoleasing_core_history.revinfo r ON co.rev = r.rev

UNION ALL

SELECT
    r.rev,
    r.revtstmp,
    r.client_ip,
    'PAYMENTS' AS entity_type,
    CASE p.revtype
        WHEN 0 THEN 'INSERT'
        WHEN 1 THEN 'UPDATE'
        WHEN 2 THEN 'DELETE'
        ELSE 'UNKNOWN'
    END AS operation_type,
    p.payment_id::VARCHAR AS entity_id
FROM autoleasing_core_history.payments_aud p
JOIN autoleasing_core_history.revinfo r ON p.rev = r.rev

ORDER BY rev DESC;