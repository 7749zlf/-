CREATE TABLE admin_channels (
  id VARCHAR(40) PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  source VARCHAR(64) NOT NULL,
  owner VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'WATCHING',
  budget DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  spent DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  revenue DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  installs INT NOT NULL DEFAULT 0,
  pay_users INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE admin_media_assets (
  id VARCHAR(40) PRIMARY KEY,
  title VARCHAR(128) NOT NULL,
  asset_type VARCHAR(32) NOT NULL,
  drama_id BIGINT NULL,
  drama_title VARCHAR(128) NOT NULL,
  duration VARCHAR(32) NOT NULL DEFAULT '-',
  file_size VARCHAR(32) NOT NULL DEFAULT '',
  status VARCHAR(32) NOT NULL DEFAULT 'REVIEWING',
  usage_scene VARCHAR(64) NOT NULL DEFAULT '',
  owner VARCHAR(64) NOT NULL DEFAULT '',
  review_note VARCHAR(255) NOT NULL DEFAULT '',
  cover_url VARCHAR(512) NOT NULL DEFAULT '',
  video_url VARCHAR(512) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_admin_media_drama (drama_id),
  INDEX idx_admin_media_status (status)
);

INSERT INTO admin_channels
(id, name, source, owner, status, budget, spent, revenue, installs, pay_users)
VALUES
('C001', '信息流 A 计划', '短视频投流', '增长组', 'RUNNING', 120000.00, 84200.00, 286400.00, 18620, 2430),
('C002', '自然流量承接', '站内推荐', '内容组', 'RUNNING', 48000.00, 31200.00, 162900.00, 22400, 1668),
('C003', '战神归来复投', '达人混剪', '增长组', 'WATCHING', 90000.00, 76900.00, 214500.00, 14280, 1974),
('C004', '古装预约冷启', '预约池', '发行组', 'PAUSED', 30000.00, 20500.00, 28400.00, 4120, 280);

INSERT INTO admin_media_assets
(id, title, asset_type, drama_id, drama_title, duration, file_size, status, usage_scene, owner, review_note, cover_url, video_url)
VALUES
('M001', '掌心风暴 01-03 集主线', '正片', (SELECT id FROM dramas WHERE title = '掌心风暴' LIMIT 1), '掌心风暴', '04:44', '186 MB', 'APPROVED', '播放页', '周倩', '审核通过，可用于前台', 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=720&q=82', ''),
('M002', '夫人她不装了 付费节点预告', '预告', (SELECT id FROM dramas WHERE title = '夫人她不装了' LIMIT 1), '夫人她不装了', '00:32', '42 MB', 'REVIEWING', '投放素材', '林然', '等待内容审核', 'https://images.unsplash.com/photo-1490750967868-88aa4486c946?auto=format&fit=crop&w=720&q=82', ''),
('M003', '归来仍是王者 高燃混剪', '混剪', (SELECT id FROM dramas WHERE title = '归来仍是王者' LIMIT 1), '归来仍是王者', '00:45', '64 MB', 'APPROVED', '渠道投放', '陈路', '审核通过，可用于前台', 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=720&q=82', ''),
('M004', '长夜明烛 首屏封面', '封面', (SELECT id FROM dramas WHERE title = '长夜明烛' LIMIT 1), '长夜明烛', '-', '8 MB', 'REJECTED', '剧场卡片', '许一', '审核驳回，请重新处理素材', 'https://images.unsplash.com/photo-1518709268805-4e9042af2176?auto=format&fit=crop&w=720&q=82', '');
