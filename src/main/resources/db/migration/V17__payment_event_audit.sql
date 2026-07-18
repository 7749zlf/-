CREATE TABLE h5_payment_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  payment_id VARCHAR(40) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  provider_trade_no VARCHAR(128) NULL,
  message VARCHAR(512) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_payment_events_payment FOREIGN KEY (payment_id) REFERENCES h5_payments(id)
);

CREATE INDEX idx_h5_payment_events_payment_created
  ON h5_payment_events (payment_id, created_at);
