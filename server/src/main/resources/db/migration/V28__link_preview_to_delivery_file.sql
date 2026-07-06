-- V28: 커미션 미리보기 재설계 — "원본 = 미리보기 자동 생성" 연결고리.
-- 작가가 원본만 업로드하면 서버가 이미지 타입에 한해 워터마크 미리보기를 자동 생성한다.
-- source_file_id = 이 미리보기를 만들어낸 납품 파일. 파일 삭제 시 미리보기도 함께 삭제(ON DELETE CASCADE).
-- 기존 수동 업로드 미리보기는 NULL 유지(연결 없음 — 표시엔 영향 없음).
ALTER TABLE commission_preview_images
    ADD COLUMN source_file_id BIGINT REFERENCES commission_files(file_id) ON DELETE CASCADE;

CREATE INDEX idx_commission_preview_images_source_file
    ON commission_preview_images (source_file_id);
