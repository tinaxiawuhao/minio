package me.test.minio.configurer;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "minio")
@Getter
@Setter
public class MinioProperties {

    private String endpoint;

    private String accesskey;

    private String secretkey;

    private String bucket;
}
