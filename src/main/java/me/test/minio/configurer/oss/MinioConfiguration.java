package me.test.minio.configurer.oss;

import io.minio.MinioClient;
import lombok.SneakyThrows;
import me.test.minio.configurer.redis.RedisUtil;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({MinioClient.class})
@EnableConfigurationProperties(OssProperties.class)
@ConditionalOnExpression("${oss.enabled}")
@ConditionalOnProperty(value = "oss.type", havingValue = "minio")
public class MinioConfiguration {


    @Bean
    @SneakyThrows
    @ConditionalOnMissingBean(CustomMinioClient.class)
    public CustomMinioClient minioClient(OssProperties ossProperties) {
        return new CustomMinioClient(MinioClient.builder()
                .endpoint(ossProperties.getEndpoint())
                .credentials(ossProperties.getAccessKey(), ossProperties.getSecretKey())
                .build());
    }

    @Bean
    @ConditionalOnBean({CustomMinioClient.class, RedisUtil.class})
    @ConditionalOnMissingBean(MinioTemplate.class)
    public MinioTemplate minioTemplate(RedisUtil redisUtil,CustomMinioClient minioClient, OssProperties ossProperties) {
        return new MinioTemplate(redisUtil,minioClient, ossProperties);
    }
}