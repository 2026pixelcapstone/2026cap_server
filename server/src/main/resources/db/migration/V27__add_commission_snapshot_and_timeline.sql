-- 커미션 거래 기록 보강:
--  (1) 스냅샷: 수락 시점의 의뢰글/작가서비스 제목·내용을 commissions에 복사 저장.
--      원글(의뢰글/서비스)이 수정·삭제돼도 거래 기록은 당시 정보로 보존된다.
--      (의뢰글 삭제 시 request_post_id는 null로 detach되므로 참조로는 제목을 못 가져옴 → 스냅샷 필요)
--  (2) 타임라인: 단계 전이 시각. created_at(=수락=IN_PROGRESS 시작)·completed_at(완료)은 기존 컬럼 활용,
--      신규로 review_requested_at(검토 요청)·cancelled_at(취소)만 추가.
ALTER TABLE commissions
    ADD COLUMN title               VARCHAR(200),
    ADD COLUMN description         TEXT,
    ADD COLUMN review_requested_at TIMESTAMP,
    ADD COLUMN cancelled_at        TIMESTAMP;
