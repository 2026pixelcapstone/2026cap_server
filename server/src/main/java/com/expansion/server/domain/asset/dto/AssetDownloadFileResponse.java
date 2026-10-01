package com.expansion.server.domain.asset.dto;

/**
 * 에셋 다운로드 파일 1개(멀티 파일). 상세 응답에 목록으로 담긴다.
 * fileUrl은 다운로드 권한(무료/구매자)이 있을 때만 채워지고, 없으면 null(파일 존재·이름·크기만 노출).
 */
public record AssetDownloadFileResponse(
        String fileName,
        long fileSize,
        String fileUrl
) {
}
