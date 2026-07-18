ALTER TABLE app_users
  ADD COLUMN password_salt VARCHAR(64) NOT NULL DEFAULT '',
  ADD COLUMN password_hash VARCHAR(128) NOT NULL DEFAULT '';

CREATE TABLE h5_sms_codes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  phone_hash VARCHAR(64) NOT NULL,
  scene VARCHAR(32) NOT NULL,
  code_hash VARCHAR(128) NOT NULL,
  request_id VARCHAR(64) NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  consumed BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  used_at TIMESTAMP NULL,
  INDEX idx_h5_sms_codes_phone_scene (phone_hash, scene, created_at)
);
