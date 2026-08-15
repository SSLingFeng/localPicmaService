package com.example.localPicmaService.tool.RustFs;

import com.example.localPicmaService.config.SystemConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * RustFS S3 客户端配置 —— 根据 SystemConfig 中的 RustFS 配置初始化 S3Client。
 */
@Component
public class RustFsConfig {

    @Autowired
    private SystemConfig systemConfig;

    private S3Client s3Client;

    @PostConstruct
    public void init() {
        String endpoint = systemConfig.getRustfsEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            System.out.println(">>> RustFS 未配置 endpoint，跳过初始化");
            return;
        }
        System.out.println(">>> RustFS 初始化: endpoint=" + endpoint + ", bucket=" + systemConfig.getRustfsBucket());

        s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1) // S3 兼容服务需要一个 region，固定即可
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                systemConfig.getRustfsAccessKey(),
                                systemConfig.getRustfsSecretKey())))
                .forcePathStyle(systemConfig.isRustfsPathStyleAccess())
                .build();
    }

    @PreDestroy
    public void destroy() {
        if (s3Client != null) {
            s3Client.close();
            System.out.println(">>> RustFS S3Client 已关闭");
        }
    }

    public S3Client getS3Client() {
        return s3Client;
    }

    public String getBucket() {
        return systemConfig.getRustfsBucket();
    }

    public String getPublicUrl() {
        return systemConfig.getRustfsPublicUrl();
    }

    public String getRustfsEndpoint() {
        return systemConfig.getRustfsEndpoint();
    }

    public boolean isConfigured() {
        return s3Client != null;
    }
}
