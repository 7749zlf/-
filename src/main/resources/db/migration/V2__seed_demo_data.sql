INSERT INTO dramas (id, title, tag, episode_count, heat_text, cover_url, status, play_count, sort_order) VALUES
(1, '掌心风暴', '都市逆袭', 68, '246.8 万', 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=720&q=82', 'PUBLISHED', 2468000, 1),
(2, '夫人她不装了', '甜宠豪门', 72, '198.2 万', 'https://images.unsplash.com/photo-1490750967868-88aa4486c946?auto=format&fit=crop&w=720&q=82', 'PUBLISHED', 1982000, 2),
(3, '归来仍是王者', '战神归来', 80, '173.4 万', 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=720&q=82', 'PUBLISHED', 1734000, 3),
(4, '长夜明烛', '古装权谋', 56, '92.6 万', 'https://images.unsplash.com/photo-1518709268805-4e9042af2176?auto=format&fit=crop&w=720&q=82', 'PUBLISHED', 926000, 4);

INSERT INTO story_pools (id, drama_id, name, status, draw_price) VALUES
('pool-1', 1, '掌心风暴故事线池', 'ENABLED', 6.00);

INSERT INTO storylines (id, drama_id, pool_id, name, rarity, description, cover_url, weight, status, sort_order) VALUES
('revenge', 1, 'pool-1', '女主复仇线', 'SSR', '女主提前拿到证据，反手布局让幕后人自食其果。', 'https://images.unsplash.com/photo-1495567720989-cebdbdd97913?auto=format&fit=crop&w=720&q=82', 2.400, 'ENABLED', 1),
('truth', 1, 'pool-1', '真相反转线', 'SSR', '旧案证据浮出水面，主角关系从误会转向并肩追查。', 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=720&q=82', 2.400, 'ENABLED', 2),
('rescue', 1, 'pool-1', '双向救赎线', 'SR', '两人从试探走向信任，补足主线隐藏的情感选择。', 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=720&q=82', 22.400, 'ENABLED', 3),
('villain', 1, 'pool-1', '反派视角线', 'SR', '从反派视角补全动机和布局，看见主线之外的暗面剧情。', 'https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?auto=format&fit=crop&w=720&q=82', 22.400, 'ENABLED', 4),
('sweet', 1, 'pool-1', '甜宠番外线', 'R', '主线之外的轻甜日常，补足角色关系的温柔片段。', 'https://images.unsplash.com/photo-1496307042754-b4aa456c4a2d?auto=format&fit=crop&w=720&q=82', 75.200, 'ENABLED', 5);

INSERT INTO episodes (id, drama_id, episode_no, title, storyline_id, cover_url, video_url, status, pay_node) VALUES
('main-1', 1, 1, '主线第 1 集', NULL, 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-5s-360p.mp4', 'PUBLISHED', FALSE),
('main-2', 1, 2, '主线第 2 集', NULL, 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-10s-360p.mp4', 'PUBLISHED', FALSE),
('main-3', 1, 3, '主线第 3 集', NULL, 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-15s-360p.mp4', 'PUBLISHED', TRUE),
('main-4', 1, 4, '主线第 4 集', NULL, 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-20s-360p.mp4', 'PUBLISHED', FALSE),
('main-5', 1, 5, '主线第 5 集', NULL, 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-30s-360p.mp4', 'PUBLISHED', FALSE),
('main-6', 1, 6, '主线第 6 集', NULL, 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=720&q=82', 'https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4', 'PUBLISHED', FALSE),
('main-7', 1, 7, '主线第 7 集', NULL, 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-5s-360p.mp4', 'PUBLISHED', FALSE),
('main-8', 1, 8, '主线第 8 集', NULL, 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-10s-360p.mp4', 'PUBLISHED', FALSE),
('main-9', 1, 9, '主线第 9 集', NULL, 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-15s-360p.mp4', 'PUBLISHED', FALSE),
('main-10', 1, 10, '主线第 10 集', NULL, 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-20s-360p.mp4', 'PUBLISHED', FALSE),
('revenge-11', 1, 11, '女主复仇线 第 11 集', 'revenge', 'https://images.unsplash.com/photo-1495567720989-cebdbdd97913?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-10s-360p.mp4', 'PUBLISHED', FALSE),
('revenge-12', 1, 12, '女主复仇线 第 12 集', 'revenge', 'https://images.unsplash.com/photo-1495567720989-cebdbdd97913?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-15s-360p.mp4', 'PUBLISHED', FALSE),
('revenge-13', 1, 13, '女主复仇线 第 13 集', 'revenge', 'https://images.unsplash.com/photo-1495567720989-cebdbdd97913?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-20s-360p.mp4', 'PUBLISHED', FALSE),
('revenge-14', 1, 14, '女主复仇线 第 14 集', 'revenge', 'https://images.unsplash.com/photo-1495567720989-cebdbdd97913?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-30s-360p.mp4', 'PUBLISHED', FALSE),
('revenge-15', 1, 15, '女主复仇线 第 15 集', 'revenge', 'https://images.unsplash.com/photo-1495567720989-cebdbdd97913?auto=format&fit=crop&w=720&q=82', 'https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4', 'PUBLISHED', FALSE),
('revenge-16', 1, 16, '女主复仇线 第 16 集', 'revenge', 'https://images.unsplash.com/photo-1495567720989-cebdbdd97913?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-5s-360p.mp4', 'PUBLISHED', FALSE),
('truth-11', 1, 11, '真相反转线 第 11 集', 'truth', 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-15s-360p.mp4', 'PUBLISHED', FALSE),
('truth-12', 1, 12, '真相反转线 第 12 集', 'truth', 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-20s-360p.mp4', 'PUBLISHED', FALSE),
('truth-13', 1, 13, '真相反转线 第 13 集', 'truth', 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-30s-360p.mp4', 'PUBLISHED', FALSE),
('truth-14', 1, 14, '真相反转线 第 14 集', 'truth', 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=720&q=82', 'https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4', 'PUBLISHED', FALSE),
('truth-15', 1, 15, '真相反转线 第 15 集', 'truth', 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-5s-360p.mp4', 'PUBLISHED', FALSE),
('truth-16', 1, 16, '真相反转线 第 16 集', 'truth', 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-10s-360p.mp4', 'PUBLISHED', FALSE),
('rescue-11', 1, 11, '双向救赎线 第 11 集', 'rescue', 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-20s-360p.mp4', 'PUBLISHED', FALSE),
('rescue-12', 1, 12, '双向救赎线 第 12 集', 'rescue', 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-30s-360p.mp4', 'PUBLISHED', FALSE),
('rescue-13', 1, 13, '双向救赎线 第 13 集', 'rescue', 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=720&q=82', 'https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4', 'PUBLISHED', FALSE),
('rescue-14', 1, 14, '双向救赎线 第 14 集', 'rescue', 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-5s-360p.mp4', 'PUBLISHED', FALSE),
('rescue-15', 1, 15, '双向救赎线 第 15 集', 'rescue', 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-10s-360p.mp4', 'PUBLISHED', FALSE),
('rescue-16', 1, 16, '双向救赎线 第 16 集', 'rescue', 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-15s-360p.mp4', 'PUBLISHED', FALSE),
('villain-11', 1, 11, '反派视角线 第 11 集', 'villain', 'https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-30s-360p.mp4', 'PUBLISHED', FALSE),
('villain-12', 1, 12, '反派视角线 第 12 集', 'villain', 'https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?auto=format&fit=crop&w=720&q=82', 'https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4', 'PUBLISHED', FALSE),
('villain-13', 1, 13, '反派视角线 第 13 集', 'villain', 'https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-5s-360p.mp4', 'PUBLISHED', FALSE),
('villain-14', 1, 14, '反派视角线 第 14 集', 'villain', 'https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-10s-360p.mp4', 'PUBLISHED', FALSE),
('villain-15', 1, 15, '反派视角线 第 15 集', 'villain', 'https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-15s-360p.mp4', 'PUBLISHED', FALSE),
('villain-16', 1, 16, '反派视角线 第 16 集', 'villain', 'https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-20s-360p.mp4', 'PUBLISHED', FALSE),
('sweet-11', 1, 11, '甜宠番外线 第 11 集', 'sweet', 'https://images.unsplash.com/photo-1496307042754-b4aa456c4a2d?auto=format&fit=crop&w=720&q=82', 'https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4', 'PUBLISHED', FALSE),
('sweet-12', 1, 12, '甜宠番外线 第 12 集', 'sweet', 'https://images.unsplash.com/photo-1496307042754-b4aa456c4a2d?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-5s-360p.mp4', 'PUBLISHED', FALSE),
('sweet-13', 1, 13, '甜宠番外线 第 13 集', 'sweet', 'https://images.unsplash.com/photo-1496307042754-b4aa456c4a2d?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-10s-360p.mp4', 'PUBLISHED', FALSE),
('sweet-14', 1, 14, '甜宠番外线 第 14 集', 'sweet', 'https://images.unsplash.com/photo-1496307042754-b4aa456c4a2d?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-15s-360p.mp4', 'PUBLISHED', FALSE),
('sweet-15', 1, 15, '甜宠番外线 第 15 集', 'sweet', 'https://images.unsplash.com/photo-1496307042754-b4aa456c4a2d?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-20s-360p.mp4', 'PUBLISHED', FALSE),
('sweet-16', 1, 16, '甜宠番外线 第 16 集', 'sweet', 'https://images.unsplash.com/photo-1496307042754-b4aa456c4a2d?auto=format&fit=crop&w=720&q=82', 'https://samplelib.com/mp4/sample-30s-360p.mp4', 'PUBLISHED', FALSE);
