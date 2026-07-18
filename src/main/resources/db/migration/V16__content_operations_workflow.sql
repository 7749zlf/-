ALTER TABLE dramas
  ADD COLUMN owner VARCHAR(64) NOT NULL DEFAULT '内容运营' AFTER status,
  ADD COLUMN review_note VARCHAR(255) NOT NULL DEFAULT '' AFTER owner;

ALTER TABLE episodes
  ADD COLUMN duration VARCHAR(32) NOT NULL DEFAULT '01:30' AFTER title,
  ADD COLUMN review_note VARCHAR(255) NOT NULL DEFAULT '' AFTER status;

CREATE TABLE admin_content_review_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  target_type VARCHAR(32) NOT NULL,
  target_id VARCHAR(128) NOT NULL,
  drama_id BIGINT NULL,
  title VARCHAR(128) NOT NULL DEFAULT '',
  status VARCHAR(32) NOT NULL,
  note VARCHAR(255) NOT NULL DEFAULT '',
  actor_id BIGINT NULL,
  actor_username VARCHAR(64) NOT NULL DEFAULT 'system',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_content_review_target (target_type, target_id, created_at),
  INDEX idx_content_review_drama (drama_id, created_at),
  CONSTRAINT fk_content_review_drama FOREIGN KEY (drama_id) REFERENCES dramas(id)
);
