package com.expansion.server.global.controller;

import com.expansion.server.global.response.ApiResponse;
import com.expansion.server.global.util.R2Uploader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
            @RequestParam("file") MultipartFile file,
            @RequestParam("folder") String folder) throws IOException {

        if (r2Uploader == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.fail("파일 업로드 기능이 비활성화되어 있습니다. (R2 미설정)"));
        }
        return ResponseEntity.ok(ApiResponse.success(r2Uploader.upload(file, folder)));
    }

    /**
     * 파일 다건 업로드
     * POST /api/files/upload/bulk
     */
    @PostMapping("/upload/bulk")
    public ResponseEntity<ApiResponse<List<String>>> uploadBulk(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("folder") String folder) throws IOException {

        if (r2Uploader == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.fail("파일 업로드 기능이 비활성화되어 있습니다. (R2 미설정)"));
        }
        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            urls.add(r2Uploader.upload(file, folder));
        }
        return ResponseEntity.ok(ApiResponse.success(urls));
    }
}
