-- 커미션 평점/리뷰 (신뢰 신호):
--   완료(COMPLETED)된 거래에 대해 의뢰자가 작가를 평가한다. 작가별로 집계해 서비스 카드/상세에 노출.
--   에셋 리뷰(asset_comments, 에셋을 평가)와 대상이 달라 별도 테이블.
--   - commission_id UNIQUE → 거래당 1리뷰(재작성은 UPDATE).
--   - artist_id 비정규화 → 작가별 평점 집계(GROUP BY artist_id)를 commissions 조인 없이 처리.
--   - rating은 SMALLINT(엔티티는 @JdbcTypeCode(SqlTypes.SMALLINT)로 매핑), 텍스트는 선택.
CREATE TABLE commission_reviews (
    review_id     BIGINT    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    commission_id BIGINT    NOT NULL REFERENCES commissions(commission_id),
    reviewer_id   BIGINT    NOT NULL REFERENCES users(user_id),
    artist_id     BIGINT    NOT NULL REFERENCES users(user_id),
    rating        SMALLINT  NOT NULL CHECK (rating BETWEEN 1 AND 5),
    content       TEXT,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    -- 거래당 1리뷰. 동시 중복 제출을 이 이름으로 식별해 409로 변환(그 외 무결성 오류는 감추지 않음).
    CONSTRAINT uk_commission_reviews_commission UNIQUE (commission_id)
);

-- 작가별 평점 집계 + 리뷰 목록(artist_id 필터 → created_at DESC 정렬) 조회용 복합 인덱스
CREATE INDEX idx_commission_reviews_artist ON commission_reviews(artist_id, created_at DESC);
