package com.expansion.server.global.util;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 클라이언트가 직접 올리는 파일(/api/files/upload)의 허용 폴더·형식 — 한 곳에서 관리.
 * 실제 저장 경로는 서버가 {폴더}/u{userId}/{uuid}.{확장자}로 만든다(경로에 올린 사람을 남겨 삭제 권한 판단에 씀).
 * Content-Type도 서버가 확장자로 정한다 — 클라이언트 값을 그대로 쓰면 text/html 등으로 우리 도메인에서 열린다.
 */
public enum UploadFolder {

    GALLERY_IMAGES("gallery/images", Kind.IMAGE),
    GALLERY_DEDICATED("gallery/dedicated", Kind.IMAGE_OR_PPIT),   // .ppit 원본 + 썸네일(PNG·GIF)
    ASSET_IMAGES("assets/images", Kind.IMAGE),
    ASSET_FILES("assets/files", Kind.ANY_SAFE);                     // 판매 파일(zip·aseprite 등) — 다운로드 전용

    private enum Kind { IMAGE, IMAGE_OR_PPIT, ANY_SAFE }

    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "gif", "image/gif",
            "webp", "image/webp");

    /** 브라우저가 문서·스크립트로 실행할 수 있는 형식 — 어느 폴더에도 받지 않는다 */
    private static final Set<String> BLOCKED = Set.of(
            "html", "htm", "xhtml", "shtml", "svg", "svgz", "xml", "xsl",
            "js", "mjs", "cjs", "php", "jsp", "asp", "aspx", "swf");

    private static final String DOWNLOAD_ONLY = "application/octet-stream";

    private final String path;
    private final Kind kind;

    UploadFolder(String path, Kind kind) {
        this.path = path;
        this.kind = kind;
    }

    public String path() {
        return path;
    }

    public static Optional<UploadFolder> of(String path) {
        return Arrays.stream(values()).filter(f -> f.path.equals(path)).findFirst();
    }

    /** 원본 파일 이름에서 확장자(소문자, 점 없음). 영문·숫자 1~10자가 아니면 빈 값 */
    public static String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return ext.matches("[a-z0-9]{1,10}") ? ext : "";
    }

    /** 이 폴더에 받을 수 있는 확장자인지 */
    public boolean allows(String ext) {
        if (ext.isEmpty() || BLOCKED.contains(ext)) return false;
        return switch (kind) {
            case IMAGE -> IMAGE_TYPES.containsKey(ext);
            case IMAGE_OR_PPIT -> IMAGE_TYPES.containsKey(ext) || ext.equals("ppit");
            case ANY_SAFE -> true;
        };
    }

    /** 저장할 Content-Type — 이미지는 확장자 기준, 그 외는 다운로드 전용 */
    public static String contentTypeOf(String ext) {
        return IMAGE_TYPES.getOrDefault(ext, DOWNLOAD_ONLY);
    }

    /** 이미지가 아니면 브라우저가 열지 않고 내려받게 한다 */
    public static boolean isInline(String ext) {
        return IMAGE_TYPES.containsKey(ext);
    }

    /** 이 사용자가 올린 파일의 key 접두사 — 삭제 권한 판단용 */
    public String ownerPrefix(Long userId) {
        return path + "/u" + userId + "/";
    }
}
