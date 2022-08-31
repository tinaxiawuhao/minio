package me.test.minio;

import me.test.minio.s3.v1.S3Utils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
class MinioApplicationTests {

    @Resource
    private S3Utils s3Utils;

    @Test
    void contextLoads() {
        s3Utils.createBucket("workshop-bucket");
        s3Utils.createLargeObject("workshop-bucket","aws-20220831","C:\\study\\aws-doc-sdk-examples-main.zip");
        s3Utils.listObjects("workshop-bucket");
    }

}
