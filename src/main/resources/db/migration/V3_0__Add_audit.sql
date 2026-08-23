CREATE SEQUENCE autoleasing_core_history.revinfo_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE autoleasing_core_history.revinfo
(
    rev       INTEGER      NOT NULL PRIMARY KEY,
    revtstmp  TIMESTAMP    NOT NULL,
    client_ip VARCHAR(100) NOT NULL
);

CREATE TABLE autoleasing_core_history.cars_aud
(
    rev          INTEGER      NOT NULL,
    revtype      SMALLINT,
    vin          VARCHAR(17) NOT NULL,
    brand        VARCHAR(100),
    model        VARCHAR(100),
    release_year INTEGER,
    price        DECIMAL,
    status       VARCHAR(100)
);

CREATE TABLE autoleasing_core_history.clients_aud
(
    rev      INTEGER NOT NULL,
    revtype  SMALLINT,
    id       BIGINT  NOT NULL,
    name     VARCHAR(200),
    passport VARCHAR(20),
    phone    VARCHAR(20)
);

CREATE TABLE autoleasing_core_history.contracts_aud
(
    rev                INTEGER NOT NULL,
    revtype            SMALLINT,
    id                 BIGINT  NOT NULL,
    car_vin            VARCHAR(17),
    client_id          BIGINT,
    duration_in_months INTEGER,
    down_payment       DECIMAL,
    interest_rate      DECIMAL,
    monthly_payment    DECIMAL,
    status             VARCHAR(100)
);

CREATE TABLE autoleasing_core_history.payments_aud
(
    rev         INTEGER NOT NULL,
    revtype     SMALLINT,
    amount      DECIMAL,
    status      VARCHAR(100),
    contract_id BIGINT  NOT NULL,
    payment_id  BIGINT  NOT NULL
);