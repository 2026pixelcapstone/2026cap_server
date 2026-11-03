package com.expansion.server.global.controller;

import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import com.expansion.server.global.response.ApiResponse;
import com.expansion.server.global.util.R2Uploader;
import com.expansion.server.global.util.UploadFolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    @Autowired(required = false)
    private R2Uploader r2Uploader;

    /**
     * 파일 단건 업로드
     * POST /api/files/upload
     */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<String>> upload(
            @AuthenticationPrincipal Long userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("folder") String folder) throws IOException {

        if (r2Uploader == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.fail(ErrorCode.FILE_UPLOAD_DISABLED));
        }
        UploadFolder target = resolveFolder(folder);
        checkType(target, file);
        return ResponseEntity.ok(ApiResponse.success(r2Uploader.upload(file, target, userId)));
    }

    /**
     * 파일 다건 업로드
     * POST /api/files/upload/bulk
     */
    @PostMapping("/upload/bulk")
    public ResponseEntity<ApiResponse<List<String>>> uploadBulk(
            @AuthenticationPrincipal Long userId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("folder") String folder) throws IOException {

        if (r2Uploader == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.fail(ErrorCode.FILE_UPLOAD_DISABLED));
        }
        UploadFolder target = resolveFolder(folder);
        files.forEach(f -> checkType(target, f));   // 하나라도 안 되면 아무것도 올리지 않음
        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            urls.add(r2Uploader.upload(file, target, userId));
        }
        return ResponseEntity.ok(ApiResponse.success(urls));
    }

    /**
     * 파일 삭제 (업로드 후 후속 작업 실패 시 고아 파일 정리용)
     * POST /api/files/delete
     * 내가 올린 파일({폴더}/u{내 id}/…)만 지운다. 그 외 URL(남의 파일·경로 규칙 이전 파일)은 조용히 건너뜀 —
     * 보상 삭제는 실패해도 본 작업을 막지 않는 용도라 오류로 돌려주지 않는다.
     */
    @PostMapping("/delete")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Long userId,
            @RequestBody List<String> fileUrls) {
        if (r2Uploader == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.fail(ErrorCode.FILE_UPLOAD_DISABLED, "파일 기능이 비활성화되어 있습니다. (R2 미설정)"));
        }
        if (fileUrls != null) {
            fileUrls.stream()
                    .filter(url -> r2Uploader.isOwnUpload(url, userId))
                    .forEach(r2Uploader::delete);
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private UploadFolder resolveFolder(String folder) {
        return UploadFolder.of(folder)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_UPLOAD_FOLDER));
    }

    private void checkType(UploadFolder folder, MultipartFile file) {
        if (!folder.allows(UploadFolder.extensionOf(file.getOriginalFilename()))) {
            throw new CustomException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
    }
}
