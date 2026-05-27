package com.expansion.server.global.controller;

import com.expansion.server.global.response.ApiResponse;
import com.expansion.server.global.util.R2Uploader;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileUploadController {

    private final R2Uploader r2Uploader;

    /**
     * 파일 단건 업로드
     * POST /api/files/upload?folder=gallery/1/images
     */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("folder") String folder) throws IOException {

        String url = r2Uploader.upload(file, folder);
        return ResponseEntity.ok(ApiResponse.success(url));
    }

    /**
     * 파일 다건 업로드
     * POST /api/files/upload/bulk?folder=gallery/1/images
     */
    @PostMapping("/upload/bulk")
    public ResponseEntity<ApiResponse<List<String>>> uploadBulk(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("folder") String folder) throws IOException {

        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            urls.add(r2Uploader.upload(file, folder));
        }
        return ResponseEntity.ok(ApiResponse.success(urls));
    }
}
