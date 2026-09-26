package com.expansion.server.domain.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 에셋 다운로드 파일 새 버전 등록 요청 (작성자만).
 * R2 업로드 후 받은 파일 URL·크기를 넘기면 새 버전을 현재 버전으로 만든다.
 */
@Getter
@NoArgsConstructor
public class AssetVersionCreateRequest {

    // R2 업로드 후 전달된 다운로드 파일 URL
    @NotBlank
    private String fileUrl;

    // 파일 크기 (bytes)
    @Positive
    private long fileSize;

    // 버전 이름(선택) — 미지정 시 서버가 v{n}.0 으로 생성
    @Size(max = 50)
    private String versionName;

    // 변경 메모(선택)
    private String changeNote;
}
