-- V24: 커미션 에스크로 — 워터마크 미리보기 URL
-- 납품 원본(file_url)은 완료(COMPLETED) 전까지 의뢰자에게 마스킹.
-- preview_url = 작가가 올린 미리보기 이미지를 서버가 워터마크+축소한 것 (검토 단계에서 노출).
ALTER TABLE commissions ADD COLUMN preview_url VARCHAR(500);
