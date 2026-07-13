INSERT IGNORE INTO story_pools (id, drama_id, name, status, draw_price) VALUES
('pool-2', 2, '夫人她不装了故事线池', 'ENABLED', 6.00),
('pool-3', 3, '归来仍是王者故事线池', 'ENABLED', 6.00),
('pool-4', 4, '长夜明烛故事线池', 'ENABLED', 6.00);

INSERT IGNORE INTO storylines (id, drama_id, pool_id, name, rarity, description, cover_url, weight, status, sort_order) VALUES
('d2-revenge', 2, 'pool-2', '女主复仇线', 'SSR', '女主提前拿到证据，反手布局让幕后人自食其果。', 'https://images.unsplash.com/photo-1495567720989-cebdbdd97913?auto=format&fit=crop&w=720&q=82', 2.400, 'ENABLED', 1),
('d2-truth', 2, 'pool-2', '真相反转线', 'SSR', '旧案证据浮出水面，主角关系从误会转向并肩追查。', 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=720&q=82', 2.400, 'ENABLED', 2),
('d2-rescue', 2, 'pool-2', '双向救赎线', 'SR', '两人从试探走向信任，补足主线隐藏的情感选择。', 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=720&q=82', 22.400, 'ENABLED', 3),
('d2-villain', 2, 'pool-2', '反派视角线', 'SR', '从反派视角补全动机和布局，看见主线之外的暗面剧情。', 'https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?auto=format&fit=crop&w=720&q=82', 22.400, 'ENABLED', 4),
('d2-sweet', 2, 'pool-2', '甜宠番外线', 'R', '主线之外的轻甜日常，补足角色关系的温柔片段。', 'https://images.unsplash.com/photo-1496307042754-b4aa456c4a2d?auto=format&fit=crop&w=720&q=82', 75.200, 'ENABLED', 5),
('d3-revenge', 3, 'pool-3', '女主复仇线', 'SSR', '女主提前拿到证据，反手布局让幕后人自食其果。', 'https://images.unsplash.com/photo-1495567720989-cebdbdd97913?auto=format&fit=crop&w=720&q=82', 2.400, 'ENABLED', 1),
('d3-truth', 3, 'pool-3', '真相反转线', 'SSR', '旧案证据浮出水面，主角关系从误会转向并肩追查。', 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=720&q=82', 2.400, 'ENABLED', 2),
('d3-rescue', 3, 'pool-3', '双向救赎线', 'SR', '两人从试探走向信任，补足主线隐藏的情感选择。', 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=720&q=82', 22.400, 'ENABLED', 3),
('d3-villain', 3, 'pool-3', '反派视角线', 'SR', '从反派视角补全动机和布局，看见主线之外的暗面剧情。', 'https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?auto=format&fit=crop&w=720&q=82', 22.400, 'ENABLED', 4),
('d3-sweet', 3, 'pool-3', '甜宠番外线', 'R', '主线之外的轻甜日常，补足角色关系的温柔片段。', 'https://images.unsplash.com/photo-1496307042754-b4aa456c4a2d?auto=format&fit=crop&w=720&q=82', 75.200, 'ENABLED', 5),
('d4-revenge', 4, 'pool-4', '女主复仇线', 'SSR', '女主提前拿到证据，反手布局让幕后人自食其果。', 'https://images.unsplash.com/photo-1495567720989-cebdbdd97913?auto=format&fit=crop&w=720&q=82', 2.400, 'ENABLED', 1),
('d4-truth', 4, 'pool-4', '真相反转线', 'SSR', '旧案证据浮出水面，主角关系从误会转向并肩追查。', 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=720&q=82', 2.400, 'ENABLED', 2),
('d4-rescue', 4, 'pool-4', '双向救赎线', 'SR', '两人从试探走向信任，补足主线隐藏的情感选择。', 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=720&q=82', 22.400, 'ENABLED', 3),
('d4-villain', 4, 'pool-4', '反派视角线', 'SR', '从反派视角补全动机和布局，看见主线之外的暗面剧情。', 'https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?auto=format&fit=crop&w=720&q=82', 22.400, 'ENABLED', 4),
('d4-sweet', 4, 'pool-4', '甜宠番外线', 'R', '主线之外的轻甜日常，补足角色关系的温柔片段。', 'https://images.unsplash.com/photo-1496307042754-b4aa456c4a2d?auto=format&fit=crop&w=720&q=82', 75.200, 'ENABLED', 5);

INSERT IGNORE INTO episodes (id, drama_id, episode_no, title, storyline_id, cover_url, video_url, status, pay_node)
SELECT CONCAT('d', d.id, '-main-', n.episode_no),
       d.id,
       n.episode_no,
       CONCAT('主线第 ', n.episode_no, ' 集'),
       NULL,
       d.cover_url,
       n.video_url,
       'PUBLISHED',
       FALSE
FROM dramas d
JOIN (
  SELECT 1 AS episode_no, 'https://samplelib.com/mp4/sample-5s-360p.mp4' AS video_url UNION ALL
  SELECT 2, 'https://samplelib.com/mp4/sample-10s-360p.mp4' UNION ALL
  SELECT 3, 'https://samplelib.com/mp4/sample-15s-360p.mp4' UNION ALL
  SELECT 4, 'https://samplelib.com/mp4/sample-20s-360p.mp4' UNION ALL
  SELECT 5, 'https://samplelib.com/mp4/sample-30s-360p.mp4' UNION ALL
  SELECT 6, 'https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4' UNION ALL
  SELECT 7, 'https://samplelib.com/mp4/sample-5s-360p.mp4' UNION ALL
  SELECT 8, 'https://samplelib.com/mp4/sample-10s-360p.mp4' UNION ALL
  SELECT 9, 'https://samplelib.com/mp4/sample-15s-360p.mp4' UNION ALL
  SELECT 10, 'https://samplelib.com/mp4/sample-20s-360p.mp4'
) n
WHERE d.id IN (2, 3, 4);

INSERT IGNORE INTO episodes (id, drama_id, episode_no, title, storyline_id, cover_url, video_url, status, pay_node)
SELECT CONCAT(s.id, '-', n.episode_no),
       s.drama_id,
       n.episode_no,
       CONCAT(s.name, ' 第 ', n.episode_no, ' 集'),
       s.id,
       s.cover_url,
       CASE MOD(s.sort_order + n.episode_no, 6)
         WHEN 0 THEN 'https://samplelib.com/mp4/sample-5s-360p.mp4'
         WHEN 1 THEN 'https://samplelib.com/mp4/sample-10s-360p.mp4'
         WHEN 2 THEN 'https://samplelib.com/mp4/sample-15s-360p.mp4'
         WHEN 3 THEN 'https://samplelib.com/mp4/sample-20s-360p.mp4'
         WHEN 4 THEN 'https://samplelib.com/mp4/sample-30s-360p.mp4'
         ELSE 'https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4'
       END,
       'PUBLISHED',
       FALSE
FROM storylines s
JOIN (
  SELECT 11 AS episode_no UNION ALL
  SELECT 12 UNION ALL
  SELECT 13 UNION ALL
  SELECT 14 UNION ALL
  SELECT 15 UNION ALL
  SELECT 16
) n
WHERE s.drama_id IN (2, 3, 4);
