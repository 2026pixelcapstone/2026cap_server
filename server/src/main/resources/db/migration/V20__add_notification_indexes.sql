-- V20: 알림 조회 성능 인덱스
-- notifications 테이블 자체는 V7에서 생성됨. 여기서는 조회 패턴용 인덱스만 추가.

-- 목록(커서 페이지네이션): user_id 필터 + notification_id DESC 정렬
CREATE INDEX idx_notifications_user_created
    ON notifications (user_id, notification_id DESC);

-- 안읽음 카운트(종 배지): 미읽음 행만 부분 인덱스로 빠르게 집계
CREATE INDEX idx_notifications_unread
    ON notifications (user_id)
    WHERE is_read = false;
