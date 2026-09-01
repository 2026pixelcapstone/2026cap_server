------ 프레임 테이블 추가 ------
CREATE TABLE frames(
    frame_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(project_id),
    frame_order INT NOT NULL DEFAULT 0,
    duration INT NOT NULL DEFAULT 100,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

------ 기존 레이어 테이블의 project_id, pixelData 수정 ------
ALTER TABLE layers ADD COLUMN frame_id BIGINT REFERENCES frames(frame_id);

-- 레이어와 같은 project_id를 공유하고 있는 프레임의 frame_id를 주입함 --
UPDATE layers l
SET frame_id = f.frame_id
FROM frames f
WHERE l.project_id = f.project_id;

-- frame_id를 NOT NULL로 변경 및 FK 설정 --
ALTER TABLE layers ALTER COLUMN frame_id SET NOT NULL;

-- 기존 project_id 컬럼 제거(외래키 자동 제거) --
ALTER TABLE layers DROP COLUMN project_id;
ALTER TABLE layers DROP COLUMN pixel_Data;
