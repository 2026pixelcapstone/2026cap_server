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
import java.util.Arrays;
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
     * 클라이언트가 올린 파일을 R2에 업로드하고 공개 URL을 반환합니다.
     * 경로·Content-Type은 서버가 정한다 — {폴더}/u{userId}/{uuid}.{확장자}, 이미지가 아니면 다운로드 전용.
     * 확장자 허용 여부는 호출부(FileUploadController)가 먼저 검사한다.
     *
     * @param file   업로드할 파일
     * @param folder 허용 폴더
     * @param userId 올린 사용자 — 경로에 남겨 삭제 권한 판단에 씀
     * @return 업로드된 파일의 공개 URL
     */
    public String upload(MultipartFile file, UploadFolder folder, Long userId) throws IOException {
        String ext = UploadFolder.extensionOf(file.getOriginalFilename());
        String key = folder.ownerPrefix(userId) + UUID.randomUUID() + "." + ext;

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(UploadFolder.contentTypeOf(ext))
                .contentDisposition(UploadFolder.isInline(ext) ? "inline" : "attachment")
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
        String key = keyOf(fileUrl);
        if (key != null) {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        }
    }

    /**
     * 우리 버킷의 공개 URL이면 객체 key(예: "profiles/3/avatar/uuid.png")를, 아니면 null을 반환합니다.
     * 경로 기반 소유권 확인(예: profiles/{userId}/ 아래인지)에 사용.
     */
    public String keyOf(String fileUrl) {
        String prefix = publicUrl + "/";
        if (fileUrl == null || !fileUrl.startsWith(prefix)) return null;
        return fileUrl.substring(prefix.length());
    }

    /**
     * 이 사용자가 /api/files/upload로 올린 파일인지 — key가 {허용 폴더}/u{userId}/로 시작하는지.
     * 클라이언트가 건넨 URL로 R2를 지우는 곳은 모두 이걸 먼저 확인한다(남의 파일 URL을 끼워 넣어 지우는 우회 방지).
     * 경로 규칙 이전에 올린 파일은 false — 지우지 않고 남겨 둔다.
     */
    public boolean isOwnUpload(String fileUrl, Long userId) {
        return Arrays.stream(UploadFolder.values()).anyMatch(f -> isOwnUpload(fileUrl, userId, f));
    }

    /** 특정 폴더 안의 내 업로드인지 — 용도가 정해진 곳(예: 에셋 판매 파일 삭제)은 폴더까지 맞춘다 */
    public boolean isOwnUpload(String fileUrl, Long userId, UploadFolder folder) {
        String key = keyOf(fileUrl);
        // '..' 등 경로 조작이 끼면 접두사 비교를 믿지 않는다
        if (key == null || userId == null || key.contains("..")) return false;
        return key.startsWith(folder.ownerPrefix(userId));
    }
}
