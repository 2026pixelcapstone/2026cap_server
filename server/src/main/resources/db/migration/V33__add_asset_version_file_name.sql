-- 에셋 다운로드 파일 버전에 원본 파일 이름 저장(히스토리/변경 기록 표시용).
-- 기존 행은 NULL(과거 업로드분은 이름 미보관). 신규 교체부터 채워짐.
ALTER TABLE asset_versions ADD COLUMN file_name VARCHAR(255);
