package me.test.minio.configurer.s3.v1.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "aws")
public class AwsProperties {

    private String endPoint;
    private String accessKey;
    private String secretKey;
    private String region;
    private String bucket;
    private Long minPartSize;
}