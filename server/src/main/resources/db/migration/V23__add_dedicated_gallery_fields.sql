-- V23: 전용 갤러리(.ppit) 지원 컬럼 추가
-- 전용(DEDICATED) 게시글이 .ppit 원본/캔버스 메타/공개 토글을 저장하기 위함.
-- 자유(FREE) 게시글은 전부 NULL. palette_data(JSONB)·thumbnail_url 은 V4에 이미 존재(여기선 채우기만).

ALTER TABLE gallery_posts
    ADD COLUMN file_url             VARCHAR(500),   -- .ppit 원본 R2 URL (정본·다운로드/열기/리믹스 소스)
    ADD COLUMN canvas_width         INT,            -- 캔버스 가로 px
    ADD COLUMN canvas_height        INT,            -- 캔버스 세로 px
    ADD COLUMN dedicated_visibility JSONB;          -- 공개 토글 {canvas,palette,layers,download}. download=false면 응답에서 file_url 마스킹
