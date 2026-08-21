package com.example.localPicmaService.page.home;

import com.example.localPicmaService.tool.RustFs.RustFsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * 首页图片接口（免登录）
 * 通过 rustfs_file 表 ID 获取图片，转为 WebP 格式返回
 */
@RestController
public class HomeImageController {

    private static final Logger log = LoggerFactory.getLogger(HomeImageController.class);

    @Autowired
    private RustFsUtil rustFsUtil;

    private static volatile boolean webpAvailable = true;

    @GetMapping("/api/public/home-image")
    public ResponseEntity<byte[]> homeImage(@RequestParam String id) {
        if (!rustFsUtil.isConfigured()) {
            return ResponseEntity.badRequest().build();
        }
        try (InputStream is = rustFsUtil.download(id)) {
            BufferedImage image = ImageIO.read(is);
            if (image == null) {
                return ResponseEntity.badRequest().build();
            }

            byte[] imageBytes;
            if (webpAvailable) {
                try {
                    imageBytes = encodeWebp(image);
                } catch (Exception e) {
                    log.warn("WebP 编码失败，降级为 PNG", e);
                    webpAvailable = false;
                    imageBytes = encodePng(image);
                }
            } else {
                imageBytes = encodePng(image);
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("image/webp"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                    .body(imageBytes);
        } catch (Exception e) {
            log.error("获取首页图片失败: id={}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private byte[] encodeWebp(BufferedImage image) throws Exception {
        // 去掉 alpha 通道（WebP writer 不支持 ARGB）
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        rgb.getGraphics().drawImage(image, 0, 0, null);

        var writers = ImageIO.getImageWritersByFormatName("webp");
        if (!writers.hasNext()) {
            throw new IllegalStateException("无 WebP 编码器");
        }
        var writer = writers.next();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            var param = writer.getDefaultWriteParam();
            writer.setOutput(ImageIO.createImageOutputStream(baos));
            writer.write(null, new javax.imageio.IIOImage(rgb, null, null), param);
            writer.dispose();
            return baos.toByteArray();
        }
    }

    private byte[] encodePng(BufferedImage image) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        }
    }
}
