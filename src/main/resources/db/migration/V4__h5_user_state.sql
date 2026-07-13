ALTER TABLE app_users
  ADD COLUMN phone VARCHAR(32) NULL,
  ADD COLUMN avatar VARCHAR(512) NOT NULL DEFAULT '',
  ADD COLUMN level VARCHAR(32) NOT NULL DEFAULT '新用户',
  ADD COLUMN balance DECIMAL(10, 2) NOT NULL DEFAULT 30.00,
  ADD COLUMN paid_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
  ADD COLUMN phone_bound BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN gender VARCHAR(32) NOT NULL DEFAULT 'unknown',
  ADD COLUMN birthday DATE NULL,
  ADD COLUMN bio VARCHAR(255) NOT NULL DEFAULT '',
  ADD COLUMN last_active_at TIMESTAMP NULL;

CREATE TABLE user_preferences (
  user_id BIGINT PRIMARY KEY,
  auto_play_next BOOLEAN NOT NULL DEFAULT TRUE,
  unlock_reminder BOOLEAN NOT NULL DEFAULT TRUE,
  muted BOOLEAN NOT NULL DEFAULT TRUE,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_preferences_user FOREIGN KEY (user_id) REFERENCES app_users(id)
);

CREATE TABLE watch_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  drama_id BIGINT NOT NULL,
  storyline_id VARCHAR(64) NULL,
  episode_id VARCHAR(64) NOT NULL,
  episode_number INT NOT NULL DEFAULT 0,
  progress_seconds INT NOT NULL DEFAULT 0,
  duration_seconds INT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_watch_user_episode (user_id, episode_id),
  CONSTRAINT fk_watch_user FOREIGN KEY (user_id) REFERENCES app_users(id),
  CONSTRAINT fk_watch_drama FOREIGN KEY (drama_id) REFERENCES dramas(id),
  CONSTRAINT fk_watch_storyline FOREIGN KEY (storyline_id) REFERENCES storylines(id),
  CONSTRAINT fk_watch_episode FOREIGN KEY (episode_id) REFERENCES episodes(id)
);

CREATE TABLE recharge_records (
  id VARCHAR(40) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  amount DECIMAL(10, 2) NOT NULL,
  method_key VARCHAR(64) NOT NULL,
  method_name VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PAID',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_recharges_user FOREIGN KEY (user_id) REFERENCES app_users(id)
);

CREATE TABLE user_follows (
  user_id BIGINT NOT NULL,
  drama_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, drama_id),
  CONSTRAINT fk_follows_user FOREIGN KEY (user_id) REFERENCES app_users(id),
  CONSTRAINT fk_follows_drama FOREIGN KEY (drama_id) REFERENCES dramas(id)
);

CREATE TABLE episode_likes (
  user_id BIGINT NOT NULL,
  episode_id VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, episode_id),
  CONSTRAINT fk_likes_user FOREIGN KEY (user_id) REFERENCES app_users(id),
  CONSTRAINT fk_likes_episode FOREIGN KEY (episode_id) REFERENCES episodes(id)
);

CREATE TABLE play_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NULL,
  device_id VARCHAR(128) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  drama_id BIGINT NULL,
  episode_id VARCHAR(64) NULL,
  storyline_id VARCHAR(64) NULL,
  payload JSON NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_play_events_user FOREIGN KEY (user_id) REFERENCES app_users(id)
);

CREATE TABLE h5_payments (
  id VARCHAR(40) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  drama_id BIGINT NOT NULL,
  storyline_id VARCHAR(64) NULL,
  amount DECIMAL(10, 2) NOT NULL,
  method_key VARCHAR(64) NOT NULL,
  method_name VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  provider_trade_no VARCHAR(128) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  paid_at TIMESTAMP NULL,
  CONSTRAINT fk_payments_user FOREIGN KEY (user_id) REFERENCES app_users(id),
  CONSTRAINT fk_payments_drama FOREIGN KEY (drama_id) REFERENCES dramas(id),
  CONSTRAINT fk_payments_storyline FOREIGN KEY (storyline_id) REFERENCES storylines(id)
);

CREATE TABLE h5_auth_tokens (
  token VARCHAR(128) PRIMARY KEY,
  refresh_token VARCHAR(128) NOT NULL UNIQUE,
  user_id BIGINT NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  revoked BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_h5_tokens_user FOREIGN KEY (user_id) REFERENCES app_users(id)
);
