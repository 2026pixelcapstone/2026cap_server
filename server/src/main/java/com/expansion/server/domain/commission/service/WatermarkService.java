package com.expansion.server.domain.commission.service;

import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.filters.ImageFilter;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 커미션 검토용 미리보기 생성 — 원본을 보호하기 위해 워터마크 + 저해상도화.
 * 결과(JPEG 바이트)만 반환하고 R2 업로드는 호출부에서 처리(테스트 용이 + R2 조건부 의존 분리).
 */
@Service
public class WatermarkService {

    private static final int MAX_EDGE = 1024;       // 긴 변 최대 px
    private static final double QUALITY = 0.7;      // JPEG 품질

    /** 이미지 → 워터마크+축소 JPEG 바이트 */
    public byte[] watermarkPreview(MultipartFile image, long commissionId) throws IOException {
        String text = "PixelPilot 미리보기 · #" + commissionId;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Thumbnails.of(image.getInputStream())
                .size(MAX_EDGE, MAX_EDGE)           // 종횡비 유지하며 박스 안에 맞춤
                .outputQuality(QUALITY)
                .outputFormat("jpg")
                .addFilter(tiledTextWatermark(text))
                .toOutputStream(baos);
        return baos.toByteArray();
    }

    // 대각선으로 반복되는 반투명 텍스트 워터마크
    private ImageFilter tiledTextWatermark(String text) {
        return (BufferedImage src) -> {
            int w = src.getWidth();
            int h = src.getHeight();
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, null);

            int fontSize = Math.max(14, w / 22);
            g.setFont(new Font("SansSerif", Font.BOLD, fontSize));
            g.setColor(Color.WHITE);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.30f)); // ≈30% 불투명
            g.rotate(Math.toRadians(-30), w / 2.0, h / 2.0);

            FontMetrics fm = g.getFontMetrics();
            int stepX = fm.stringWidth(text) + 60;
            int stepY = fontSize * 4;
            for (int y = -h; y < h * 2; y += stepY) {
                for (int x = -w; x < w * 2; x += stepX) {
                    g.drawString(text, x, y);
                }
            }
            g.dispose();
            return out;
        };
    }
}
