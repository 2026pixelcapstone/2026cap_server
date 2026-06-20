package com.expansion.server.global.util;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Component
@ConditionalOnBean(S3Client.class)
@RequiredArgsConstructor
public class R2Uploader {

    private final S3Client s3Client;

    @Value("${r2.bucket}")
    private String bucket;

    @Value("${r2.endpoint}")
    private String endpoint;

    @Value("${r2.public-url}")
    private String publicUrl;

    /**
     * 파일을 R2에 업로드하고 공개 URL을 반환합니다.
     *
     * @param file   업로드할 파일
     * @param folder 저장 경로 (예: "gallery/1/images", "assets/2/files")
     * @return 업로드된 파일의 공개 URL
     */
    public String upload(MultipartFile file, String folder) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : "";
        String key = folder + "/" + UUID.randomUUID() + extension;

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        // Public Development URL로 반환
        return publicUrl + "/" + key;
    }

    /**
     * 서버에서 생성한 바이트(예: 워터마크 미리보기)를 R2에 업로드하고 공개 URL을 반환합니다.
     */
    public String uploadBytes(byte[] bytes, String contentType, String extension, String folder) {
        String key = folder + "/" + UUID.randomUUID() + extension;
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength((long) bytes.length)
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(bytes));
        return publicUrl + "/" + key;
    }

    /**
     * R2에서 파일을 삭제합니다.
     *
     * @param fileUrl 삭제할 파일의 URL
     */
    public void delete(String fileUrl) {
        // URL에서 key 추출
        String prefix = publicUrl + "/";
        if (fileUrl.startsWith(prefix)) {
            String key = fileUrl.substring(prefix.length());
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        }
    }
}
