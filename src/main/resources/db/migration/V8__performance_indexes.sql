CREATE INDEX idx_dramas_status_sort_order
  ON dramas (status, sort_order, id);

CREATE INDEX idx_episodes_status_drama_storyline_episode
  ON episodes (status, drama_id, storyline_id, episode_no);

CREATE INDEX idx_storylines_status_drama_sort_order
  ON storylines (status, drama_id, sort_order, id);

CREATE INDEX idx_watch_history_user_updated
  ON watch_history (user_id, updated_at);

CREATE INDEX idx_orders_user_created
  ON orders (user_id, created_at);

CREATE INDEX idx_recharge_records_user_created
  ON recharge_records (user_id, created_at);

CREATE INDEX idx_user_unlocks_user_created
  ON user_unlocks (user_id, created_at);

CREATE INDEX idx_episode_likes_user_created
  ON episode_likes (user_id, created_at);

CREATE INDEX idx_play_events_device_created
  ON play_events (device_id, created_at);

CREATE INDEX idx_h5_auth_tokens_user_expires
  ON h5_auth_tokens (user_id, expires_at, revoked);

CREATE INDEX idx_h5_payments_user_created
  ON h5_payments (user_id, created_at, status);

CREATE INDEX idx_admin_auth_tokens_admin_expires
  ON admin_auth_tokens (admin_id, expires_at, revoked);
