-- Generic

CREATE TABLE IF NOT EXISTS jobb
(
    id           BIGSERIAL PRIMARY KEY,
    data         VARCHAR NOT NULL,
    klartidpunkt TIMESTAMPTZ
);

-- GDL

CREATE TABLE IF NOT EXISTS forandringsarende
(
    dataleveransidentitet UUID PRIMARY KEY,
    request_xml           VARCHAR     NOT NULL,
    mottagentidpunkt      TIMESTAMPTZ NOT NULL,
    http_status           VARCHAR(3)  NOT NULL,
    behandladtidpunkt     TIMESTAMPTZ
);

-- Surval

CREATE TABLE IF NOT EXISTS surval_jobb
(
    id               UUID PRIMARY KEY,
    mottagentidpunkt TIMESTAMPTZ NOT NULL,
    data             VARCHAR     NOT NULL,
    status           VARCHAR(20) NOT NULL,
    klartidpunkt     TIMESTAMPTZ
);
