-- =====================================================================
-- JollyCart - struktura bazy danych (MySQL 8.x, InnoDB, utf8mb4)
--
-- Odwzorowuje encje JPA z projektu Spring Boot (pakiet pl.jollycart),
-- tak jak wygenerowalby je Hibernate (spring.jpa.hibernate.ddl-auto):
--   - String(length=n)        -> varchar(n)
--   - @Enumerated(STRING)     -> enum('...')   (Hibernate 6/7 na MySQL)
--   - boolean                 -> bit(1)
--   - LocalDateTime           -> datetime(6)
--   - BigDecimal(10,2)        -> decimal(10,2)
--   - Double                  -> double
--   - GenerationType.IDENTITY -> bigint auto_increment
--
-- Uruchomienie dla wersji Servlet API:
--   mysql -u JollyCart_User -p jollycart_servlet < 01_schema.sql
--
-- Uwagi:
--  * Kolumny created_at / updated_at ustawia KOD APLIKACJI (w Springu
--    robia to @PrePersist/@PreUpdate), dlatego nie maja DEFAULT.
--  * Brak ON DELETE CASCADE - tak jak w Hibernate. Kaskadowe usuwanie
--    (np. uczestnicy rozmowy) realizuje kod aplikacji.
--  * Tabele orders, order_disputes, dispute_images, seller_accounts i
--    audit_logs celowo NIE maja kluczy obcych (historia ma przetrwac
--    usuniecie ogloszen i kont) - zgodnie z komentarzami w encjach.
--  * Dane poczatkowe (kategorie, konto administratora) tworzy kod
--    aplikacji przy starcie (CategoryInitializer, AdminInitializer).
-- =====================================================================

USE jollycart_servlet;

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id                       BIGINT        NOT NULL AUTO_INCREMENT,
    nickname                 VARCHAR(30)   NOT NULL,
    email                    VARCHAR(255)  NOT NULL,
    password_hash            VARCHAR(255),
    auth_provider            ENUM('LOCAL','GOOGLE') NOT NULL,
    provider_id              VARCHAR(255),
    avatar_url               VARCHAR(1000),
    phone                    VARCHAR(30),
    location                 VARCHAR(100),
    role                     VARCHAR(20)   NOT NULL DEFAULT 'USER',
    status                   VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    ban_reason               VARCHAR(500),
    banned_at                DATETIME(6),
    banned_by_id             BIGINT,
    password_change_required BIT(1)        NOT NULL DEFAULT 0,
    created_at               DATETIME(6)   NOT NULL,
    updated_at               DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_nickname UNIQUE (nickname),
    CONSTRAINT uk_users_email    UNIQUE (email),
    CONSTRAINT uk_users_provider UNIQUE (auth_provider, provider_id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- categories
-- ---------------------------------------------------------------------
CREATE TABLE categories (
    id    BIGINT       NOT NULL AUTO_INCREMENT,
    name  VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_categories_name UNIQUE (name)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- pending_registrations (rejestracja w toku, przed potwierdzeniem e-mail)
-- ---------------------------------------------------------------------
CREATE TABLE pending_registrations (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(255) NOT NULL,
    nickname      VARCHAR(30)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    code_hash     VARCHAR(255) NOT NULL,
    expires_at    DATETIME(6)  NOT NULL,
    last_sent_at  DATETIME(6)  NOT NULL,
    attempts      INTEGER      NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_pending_registrations_email UNIQUE (email)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- stored_files (pliki: CV, zalaczniki czatu, awatary - metadane)
-- ---------------------------------------------------------------------
CREATE TABLE stored_files (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_name   VARCHAR(255) NOT NULL,
    content_type       VARCHAR(100) NOT NULL,
    file_size          BIGINT       NOT NULL,
    created_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stored_files_stored_file_name UNIQUE (stored_file_name)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- offers
-- ---------------------------------------------------------------------
CREATE TABLE offers (
    id                      BIGINT        NOT NULL AUTO_INCREMENT,
    title                   VARCHAR(150)  NOT NULL,
    description             VARCHAR(5000) NOT NULL,
    price                   DECIMAL(10,2),
    location                VARCHAR(150),
    location_latitude       DOUBLE,
    location_longitude      DOUBLE,
    location_radius_meters  INTEGER,
    type                    ENUM('SALE','SERVICE','JOB') NOT NULL,
    status                  ENUM('ACTIVE','INACTIVE','SOLD','RESERVED') NOT NULL,
    online_payment_enabled  BIT(1)        NOT NULL DEFAULT 0,
    shipping_cost           DECIMAL(10,2),
    user_id                 BIGINT        NOT NULL,
    category_id             BIGINT        NOT NULL,
    created_at              DATETIME(6)   NOT NULL,
    updated_at              DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_offers_user     FOREIGN KEY (user_id)     REFERENCES users (id),
    CONSTRAINT fk_offers_category FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- offer_images
-- ---------------------------------------------------------------------
CREATE TABLE offer_images (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    offer_id         BIGINT       NOT NULL,
    file_name        VARCHAR(255) NOT NULL,
    stored_file_name VARCHAR(255) NOT NULL,
    content_type     VARCHAR(100) NOT NULL,
    file_size        BIGINT       NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_offer_images_offer FOREIGN KEY (offer_id) REFERENCES offers (id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- job_applications
-- ---------------------------------------------------------------------
CREATE TABLE job_applications (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    offer_id    BIGINT        NOT NULL,
    user_id     BIGINT        NOT NULL,
    cv_file_id  BIGINT,
    status      ENUM('PENDING','ACCEPTED','REJECTED') NOT NULL,
    message     VARCHAR(5000),
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_job_application_offer_user UNIQUE (offer_id, user_id),
    CONSTRAINT uk_job_applications_cv_file   UNIQUE (cv_file_id),
    CONSTRAINT fk_job_applications_offer   FOREIGN KEY (offer_id)   REFERENCES offers (id),
    CONSTRAINT fk_job_applications_user    FOREIGN KEY (user_id)    REFERENCES users (id),
    CONSTRAINT fk_job_applications_cv_file FOREIGN KEY (cv_file_id) REFERENCES stored_files (id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- conversations / conversation_participants / messages
-- ---------------------------------------------------------------------
CREATE TABLE conversations (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    offer_id    BIGINT,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE TABLE conversation_participants (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    conversation_id  BIGINT      NOT NULL,
    user_id          BIGINT      NOT NULL,
    joined_at        DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_conversation_user UNIQUE (conversation_id, user_id),
    CONSTRAINT fk_conv_participants_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id),
    CONSTRAINT fk_conv_participants_user         FOREIGN KEY (user_id)         REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE messages (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    conversation_id  BIGINT        NOT NULL,
    sender_id        BIGINT        NOT NULL,
    content          VARCHAR(5000) NOT NULL,
    attachment_id    BIGINT,
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id),
    CONSTRAINT fk_messages_sender       FOREIGN KEY (sender_id)       REFERENCES users (id),
    CONSTRAINT fk_messages_attachment   FOREIGN KEY (attachment_id)   REFERENCES stored_files (id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- notifications
-- ---------------------------------------------------------------------
CREATE TABLE notifications (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    recipient_id     BIGINT      NOT NULL,
    sender_id        BIGINT      NOT NULL,
    conversation_id  BIGINT      NOT NULL,
    message_id       BIGINT      NOT NULL,
    type             ENUM('MESSAGE') NOT NULL,
    is_read          BIT(1)      NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notifications_recipient    FOREIGN KEY (recipient_id)    REFERENCES users (id),
    CONSTRAINT fk_notifications_sender       FOREIGN KEY (sender_id)       REFERENCES users (id),
    CONSTRAINT fk_notifications_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id),
    CONSTRAINT fk_notifications_message      FOREIGN KEY (message_id)      REFERENCES messages (id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- user_ratings
-- ---------------------------------------------------------------------
CREATE TABLE user_ratings (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    rater_id        BIGINT      NOT NULL,
    rated_user_id   BIGINT      NOT NULL,
    stars           INTEGER     NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_ratings_rater_rated UNIQUE (rater_id, rated_user_id),
    CONSTRAINT fk_user_ratings_rater FOREIGN KEY (rater_id)      REFERENCES users (id),
    CONSTRAINT fk_user_ratings_rated FOREIGN KEY (rated_user_id) REFERENCES users (id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- audit_logs (bez kluczy obcych - wpisy przezywaja usuniecie uzytkownika)
-- ---------------------------------------------------------------------
CREATE TABLE audit_logs (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    actor_id        BIGINT,
    actor_nickname  VARCHAR(30),
    action          ENUM('USER_BANNED','USER_UNBANNED','USER_DELETED','ROLE_CHANGED','OFFER_DELETED') NOT NULL,
    target_type     VARCHAR(20),
    target_id       BIGINT,
    details         VARCHAR(1000),
    created_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- orders (bez kluczy obcych - historia platnosci ma przetrwac usuniecia)
-- ---------------------------------------------------------------------
CREATE TABLE orders (
    id                        BIGINT        NOT NULL AUTO_INCREMENT,
    offer_id                  BIGINT        NOT NULL,
    offer_title               VARCHAR(150)  NOT NULL,
    buyer_id                  BIGINT        NOT NULL,
    seller_id                 BIGINT        NOT NULL,
    amount                    DECIMAL(10,2) NOT NULL,
    shipping_cost             DECIMAL(10,2),
    service_fee               DECIMAL(10,2),
    payment_fee               DECIMAL(10,2),
    total_amount              DECIMAL(10,2),
    currency                  VARCHAR(3)    NOT NULL,
    status                    ENUM('PENDING_PAYMENT','PAID','SHIPPED','DELIVERED',
                                   'COMPLETED','DISPUTED','CANCELLED','REFUNDED') NOT NULL,
    stripe_session_id         VARCHAR(255),
    stripe_checkout_url       VARCHAR(2000),
    stripe_payment_intent_id  VARCHAR(255),
    stripe_charge_id          VARCHAR(255),
    stripe_transfer_id        VARCHAR(255),
    checkout_expires_at       DATETIME(6),
    paid_at                   DATETIME(6),
    ship_deadline             DATETIME(6),
    shipped_at                DATETIME(6),
    delivered_at              DATETIME(6),
    confirm_deadline          DATETIME(6),
    completed_at              DATETIME(6),
    dispute_reason            VARCHAR(500),
    shipping_city             VARCHAR(100),
    shipping_street           VARCHAR(150),
    shipping_postal_code      VARCHAR(10),
    shipping_phone            VARCHAR(20),
    buyer_unread              BIT(1)        NOT NULL DEFAULT 0,
    seller_unread             BIT(1)        NOT NULL DEFAULT 0,
    created_at                DATETIME(6)   NOT NULL,
    updated_at                DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_orders_stripe_session_id UNIQUE (stripe_session_id)
) ENGINE=InnoDB;

CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_buyer  ON orders (buyer_id);
CREATE INDEX idx_orders_seller ON orders (seller_id);

-- ---------------------------------------------------------------------
-- seller_accounts (konta sprzedawcow w Stripe Connect)
-- ---------------------------------------------------------------------
CREATE TABLE seller_accounts (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    user_id            BIGINT      NOT NULL,
    stripe_account_id  VARCHAR(64) NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_seller_accounts_user   UNIQUE (user_id),
    CONSTRAINT uk_seller_accounts_stripe UNIQUE (stripe_account_id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- order_disputes / dispute_images (bez kluczy obcych)
-- ---------------------------------------------------------------------
CREATE TABLE order_disputes (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    order_id          BIGINT        NOT NULL,
    reporter_id       BIGINT        NOT NULL,
    title             VARCHAR(150)  NOT NULL,
    description       VARCHAR(2000) NOT NULL,
    status            ENUM('OPEN','RESOLVED') NOT NULL,
    resolution        ENUM('REFUND_BUYER','PAY_SELLER'),
    resolver_id       BIGINT,
    resolver_nickname VARCHAR(30),
    resolver_note     VARCHAR(500),
    created_at        DATETIME(6)   NOT NULL,
    resolved_at       DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_order_disputes_order UNIQUE (order_id)
) ENGINE=InnoDB;

CREATE INDEX idx_order_disputes_status ON order_disputes (status);

CREATE TABLE dispute_images (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    dispute_id        BIGINT       NOT NULL,
    file_name         VARCHAR(255) NOT NULL,
    stored_file_name  VARCHAR(255) NOT NULL,
    content_type      VARCHAR(100) NOT NULL,
    file_size         BIGINT       NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE INDEX idx_dispute_images_dispute ON dispute_images (dispute_id);