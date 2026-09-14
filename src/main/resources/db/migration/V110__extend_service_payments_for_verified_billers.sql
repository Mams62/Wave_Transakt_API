ALTER TABLE service_payments
    ADD COLUMN customer_phone VARCHAR(20),
    ADD COLUMN service_option VARCHAR(20);

CREATE INDEX idx_service_payment_service_kind
    ON service_payments(service_kind);
