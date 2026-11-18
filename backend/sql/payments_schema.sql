-- Schemat platnosci i zamowien dla wersji Servlet API (etap 1).
-- Nazwy tabel i kolumn takie same jak w wersji Spring Boot (tam tworzy je Hibernate, ddl-auto=update),
-- wiec ten sam front i ta sama baza dzialaja w obu wersjach. Skrypt mozna uruchamiac wielokrotnie.

CREATE TABLE IF NOT EXISTS seller_accounts (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    user_id           BIGINT       NOT NULL,
    stripe_account_id VARCHAR(64)  NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_seller_accounts_user (user_id),
    UNIQUE KEY uk_seller_accounts_stripe (stripe_account_id)
);

CREATE TABLE IF NOT EXISTS orders (
    id                       BIGINT         NOT NULL AUTO_INCREMENT,
    offer_id                 BIGINT         NOT NULL,
    offer_title              VARCHAR(150)   NOT NULL,
    buyer_id                 BIGINT         NOT NULL,
    seller_id                BIGINT         NOT NULL,
    amount                   DECIMAL(10,2)  NOT NULL,   -- cena towaru
    shipping_cost            DECIMAL(10,2)  NULL,
    service_fee              DECIMAL(10,2)  NULL,
    payment_fee              DECIMAL(10,2)  NULL,
    total_amount             DECIMAL(10,2)  NULL,       -- kwota placona przez kupujacego
    currency                 VARCHAR(3)     NOT NULL,
    status                   VARCHAR(20)    NOT NULL,
    stripe_session_id        VARCHAR(255)   NULL,
    stripe_checkout_url      VARCHAR(2000)  NULL,
    stripe_payment_intent_id VARCHAR(255)   NULL,
    stripe_charge_id         VARCHAR(255)   NULL,
    stripe_transfer_id       VARCHAR(255)   NULL,
    checkout_expires_at      DATETIME(6)    NULL,
    paid_at                  DATETIME(6)    NULL,
    ship_deadline            DATETIME(6)    NULL,
    shipped_at               DATETIME(6)    NULL,
    delivered_at             DATETIME(6)    NULL,
    confirm_deadline         DATETIME(6)    NULL,
    completed_at             DATETIME(6)    NULL,
    dispute_reason           VARCHAR(500)   NULL,
    shipping_city            VARCHAR(100)   NULL,
    shipping_street          VARCHAR(150)   NULL,
    shipping_postal_code     VARCHAR(10)    NULL,
    shipping_phone           VARCHAR(20)    NULL,
    buyer_unread             BIT(1)         NOT NULL DEFAULT 0,
    seller_unread            BIT(1)         NOT NULL DEFAULT 0,
    created_at               DATETIME(6)    NOT NULL,
    updated_at               DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_stripe_session (stripe_session_id),
    KEY idx_orders_status (status),
    KEY idx_orders_buyer (buyer_id),
    KEY idx_orders_seller (seller_id)
);
