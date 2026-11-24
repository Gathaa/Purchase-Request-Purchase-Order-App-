CREATE DATABASE IF NOT EXISTS PurchaseOrder;
USE PurchaseOrder;
CREATE TABLE purchase_requests (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    requester_name  VARCHAR(80)  NOT NULL,
    department      ENUM('IT','Operations','Finance','HR') NOT NULL,
    needed_by_date  DATE         NOT NULL,
    supplier_name   VARCHAR(150) NOT NULL,
    justification   VARCHAR(1000) NOT NULL,
    status          VARCHAR(30)  NOT NULL DEFAULT 'Draft',
    grand_total     DECIMAL(16,2) NOT NULL DEFAULT 0.00,
    version         BIGINT       NOT NULL DEFAULT 0,  
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_pr_requester_length     CHECK (CHAR_LENGTH(requester_name) BETWEEN 3 AND 80),
    CONSTRAINT chk_pr_justification_length CHECK (CHAR_LENGTH(justification) BETWEEN 10 AND 1000),
    CONSTRAINT chk_pr_status CHECK (status IN
        ('Draft','Submitted','Approved','Rejected','Cancelled','Ordered')),
    INDEX idx_pr_status_department (status, department)
);
CREATE TABLE purchase_request_items (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    purchase_request_id BIGINT        NOT NULL,
    name                VARCHAR(200)  NOT NULL,
    quantity            INT           NOT NULL,
    unit_price          DECIMAL(10,2) NOT NULL,
    line_total          DECIMAL(14,2) NOT NULL,
    CONSTRAINT fk_request_items_request
        FOREIGN KEY (purchase_request_id) REFERENCES purchase_requests(id),
    CONSTRAINT chk_pri_quantity   CHECK (quantity BETWEEN 1 AND 999),
    CONSTRAINT chk_pri_unit_price CHECK (unit_price > 0)
);
CREATE TABLE status_histories (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    purchase_request_id BIGINT        NOT NULL,
    from_status         VARCHAR(30)   NULL,
    to_status           VARCHAR(30)   NOT NULL,
    comments            VARCHAR(1000) NOT NULL,
    changed_at          TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_status_history_request
        FOREIGN KEY (purchase_request_id) REFERENCES purchase_requests(id),
    CONSTRAINT chk_sh_comment_length CHECK (CHAR_LENGTH(comments) >= 5)
);
CREATE TABLE po_sequences (
    `year` INT PRIMARY KEY,
    `last_value` INT NOT NULL DEFAULT 0
);
CREATE TABLE purchase_orders (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    purchase_request_id BIGINT        NOT NULL UNIQUE,   -- one PO per request
    po_number           VARCHAR(50)   NOT NULL UNIQUE,   -- e.g. PO-2026-0001
    po_date             DATE          NOT NULL,
    supplier_name       VARCHAR(150)  NOT NULL,
    requester_name      VARCHAR(80)   NOT NULL,
    department          VARCHAR(50)   NOT NULL,
    needed_by_date      DATE          NOT NULL,
    grand_total         DECIMAL(16,2) NOT NULL,
    status              VARCHAR(30)   NOT NULL DEFAULT 'Draft PO',
    issued_at           DATETIME      NULL,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_purchase_order_request
        FOREIGN KEY (purchase_request_id) REFERENCES purchase_requests(id),
    CONSTRAINT chk_po_status CHECK (status IN ('Draft PO','Issued')),
    CONSTRAINT chk_po_issued CHECK (
        (status = 'Issued' AND issued_at IS NOT NULL) OR
        (status = 'Draft PO' AND issued_at IS NULL))
);
CREATE TABLE purchase_order_items (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    purchase_order_id BIGINT        NOT NULL,
    name              VARCHAR(200)  NOT NULL,
    quantity          INT           NOT NULL,
    unit_price        DECIMAL(10,2) NOT NULL,
    line_total        DECIMAL(14,2) NOT NULL,
    CONSTRAINT fk_po_items_po
        FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
    CONSTRAINT chk_poi_quantity   CHECK (quantity BETWEEN 1 AND 999),
    CONSTRAINT chk_poi_unit_price CHECK (unit_price > 0)
);CREATE DATABASE IF NOT EXISTS PurchaseOrder;
USE PurchaseOrder;
CREATE TABLE purchase_requests (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    requester_name  VARCHAR(80)  NOT NULL,
    department      ENUM('IT','Operations','Finance','HR') NOT NULL,
    needed_by_date  DATE         NOT NULL,
    supplier_name   VARCHAR(150) NOT NULL,
    justification   VARCHAR(1000) NOT NULL,
    status          VARCHAR(30)  NOT NULL DEFAULT 'Draft',
    grand_total     DECIMAL(16,2) NOT NULL DEFAULT 0.00,
    version         BIGINT       NOT NULL DEFAULT 0,   -- optimistic locking (@Version)
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_pr_requester_length     CHECK (CHAR_LENGTH(requester_name) BETWEEN 3 AND 80),
    CONSTRAINT chk_pr_justification_length CHECK (CHAR_LENGTH(justification) BETWEEN 10 AND 1000),
    CONSTRAINT chk_pr_status CHECK (status IN
        ('Draft','Submitted','Approved','Rejected','Cancelled','Ordered')),
    INDEX idx_pr_status_department (status, department)
);
CREATE TABLE purchase_request_items (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    purchase_request_id BIGINT        NOT NULL,
    name                VARCHAR(200)  NOT NULL,
    quantity            INT           NOT NULL,
    unit_price          DECIMAL(10,2) NOT NULL,
    line_total          DECIMAL(14,2) NOT NULL,
    CONSTRAINT fk_request_items_request
        FOREIGN KEY (purchase_request_id) REFERENCES purchase_requests(id),
    CONSTRAINT chk_pri_quantity   CHECK (quantity BETWEEN 1 AND 999),
    CONSTRAINT chk_pri_unit_price CHECK (unit_price > 0)
);
CREATE TABLE status_histories (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    purchase_request_id BIGINT        NOT NULL,
    from_status         VARCHAR(30)   NULL,
    to_status           VARCHAR(30)   NOT NULL,
    comments            VARCHAR(1000) NOT NULL,
    changed_at          TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_status_history_request
        FOREIGN KEY (purchase_request_id) REFERENCES purchase_requests(id),
    CONSTRAINT chk_sh_comment_length CHECK (CHAR_LENGTH(comments) >= 5)
);
-- =========================
-- PO NUMBER SEQUENCE (one row per year)
-- =========================
CREATE TABLE po_sequences (
    `year` INT PRIMARY KEY,
    `last_value` INT NOT NULL DEFAULT 0
);	
-- =========================
-- PURCHASE ORDERS
-- =========================
CREATE TABLE purchase_orders (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    purchase_request_id BIGINT        NOT NULL UNIQUE,   -- one PO per request
    po_number           VARCHAR(50)   NOT NULL UNIQUE,   -- e.g. PO-2026-0001
    po_date             DATE          NOT NULL,
    supplier_name       VARCHAR(150)  NOT NULL,
    requester_name      VARCHAR(80)   NOT NULL,
    department          VARCHAR(50)   NOT NULL,
    needed_by_date      DATE          NOT NULL,
    grand_total         DECIMAL(16,2) NOT NULL,
    status              VARCHAR(30)   NOT NULL DEFAULT 'Draft PO',
    issued_at           DATETIME      NULL,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_purchase_order_request
        FOREIGN KEY (purchase_request_id) REFERENCES purchase_requests(id),
    CONSTRAINT chk_po_status CHECK (status IN ('Draft PO','Issued')),
    CONSTRAINT chk_po_issued CHECK (
        (status = 'Issued' AND issued_at IS NOT NULL) OR
        (status = 'Draft PO' AND issued_at IS NULL))
);
CREATE TABLE purchase_order_items (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    purchase_order_id BIGINT        NOT NULL,
    name              VARCHAR(200)  NOT NULL,
    quantity          INT           NOT NULL,
    unit_price        DECIMAL(10,2) NOT NULL,
    line_total        DECIMAL(14,2) NOT NULL,
    CONSTRAINT fk_po_items_po
        FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
    CONSTRAINT chk_poi_quantity   CHECK (quantity BETWEEN 1 AND 999),
    CONSTRAINT chk_poi_unit_price CHECK (unit_price > 0)
);

