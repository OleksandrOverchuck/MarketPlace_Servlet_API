-- Schemat zgloszen problemow z zamowieniami (etap 6). Nazwy jak w wersji Spring Boot (Hibernate, ddl-auto=update).
-- Skrypt mozna uruchamiac wielokrotnie. Wymaga wczesniejszego payments_schema.sql.

CREATE TABLE IF NOT EXISTS order_disputes (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    order_id          BIGINT         NOT NULL,
    reporter_id       BIGINT         NOT NULL,
    title             VARCHAR(150)   NOT NULL,
    description       VARCHAR(2000)  NOT NULL,
    status            VARCHAR(20)    NOT NULL,   -- OPEN / RESOLVED
    resolution        VARCHAR(20)    NULL,       -- REFUND_BUYER / PAY_SELLER
    resolver_id       BIGINT         NULL,
    resolver_nickname VARCHAR(30)    NULL,
    resolver_note     VARCHAR(500)   NULL,
    created_at        DATETIME(6)    NOT NULL,
    resolved_at       DATETIME(6)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_disputes_order (order_id),
    KEY idx_order_disputes_status (status)
);

CREATE TABLE IF NOT EXISTS dispute_images (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    dispute_id       BIGINT        NOT NULL,
    file_name        VARCHAR(255)  NOT NULL,
    stored_file_name VARCHAR(255)  NOT NULL,
    content_type     VARCHAR(100)  NOT NULL,
    file_size        BIGINT        NOT NULL,
    created_at       DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_dispute_images_dispute (dispute_id)
);
