-- V25: 커미션 검토용 미리보기 다중 이미지
-- 기존 commissions.preview_url(단일) → commission_preview_images(다중)로 확장.
-- commission_service_images / request_post_images 와 동일 패턴(image_url + sort_order).

CREATE TABLE commission_preview_images (
    preview_image_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    commission_id    BIGINT       NOT NULL REFERENCES commissions(commission_id) ON DELETE CASCADE,
    image_url        VARCHAR(500) NOT NULL,
    sort_order       INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_commission_preview_images_commission
    ON commission_preview_images (commission_id, sort_order);

-- 기존 단일 preview_url 이관(있으면 1행). commissions.preview_url 컬럼은 보존(미사용).
INSERT INTO commission_preview_images (commission_id, image_url, sort_order)
SELECT commission_id, preview_url, 0
FROM commissions
WHERE preview_url IS NOT NULL;
