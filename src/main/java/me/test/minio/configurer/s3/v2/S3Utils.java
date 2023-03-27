//package me.test.minio.configurer.s3.v2;
//
//import lombok.extern.slf4j.Slf4j;
//import me.test.minio.configurer.s3.v1.config.AwsProperties;
//import org.springframework.boot.context.properties.EnableConfigurationProperties;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.stereotype.Component;
//import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
//import software.amazon.awssdk.auth.credentials.AwsCredentials;
//import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
//import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain;
//import software.amazon.awssdk.core.internal.util.Mimetype;
//import software.amazon.awssdk.core.sync.RequestBody;
//import software.amazon.awssdk.regions.Region;
//import software.amazon.awssdk.services.s3.S3Client;
//import software.amazon.awssdk.services.s3.model.GetObjectRequest;
//import software.amazon.awssdk.services.s3.model.PutObjectRequest;
//import software.amazon.awssdk.services.s3.model.PutObjectResponse;
//import software.amazon.awssdk.services.s3.model.S3Exception;
//import software.amazon.awssdk.services.s3.presigner.S3Presigner;
//import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
//import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
//
//import javax.annotation.PostConstruct;
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.IOException;
//import java.time.Duration;
//
///**
// * @author test
// * @date 2021/3/23
// */
//@Component
//@Configuration
//@EnableConfigurationProperties({AwsProperties.class})
//@Slf4j
//public class S3Utils {
//
//    private final AwsProperties awsProperties;
//
//    private S3Client s3Client;
//
//    public S3Utils(AwsProperties awsProperties) {
//        this.awsProperties = awsProperties;
//    }
//
//    @PostConstruct
//    public void init() {
//        s3Client = S3Client.builder()
//                .credentialsProvider(getAwsCredentialsProviderChain())
//                .region(Region.of(awsProperties.getRegion()))
//                .build();
//    }
//
//    // 获取aws供应商凭据
//    private AwsCredentialsProviderChain getAwsCredentialsProviderChain() {
//        return AwsCredentialsProviderChain
//                .builder()
//                .addCredentialsProvider(new AwsCredentialsProvider() {
//                    @Override
//                    public AwsCredentials resolveCredentials() {
//                        return AwsBasicCredentials.create(awsProperties.getAccessKey(), awsProperties.getSecretKey());
//                    }
//                }).build();
//    }
//
//    public String putS3Object(String bucketName, String objectKey, String objectPath) {
//        try {
//            String mimetype = Mimetype.getInstance().getMimetype(new File(objectPath));
//            PutObjectRequest putOb = PutObjectRequest.builder()
//                    .bucket(bucketName)
//                    .key(objectKey)
//                    .contentType(mimetype)
//                    .build();
//            PutObjectResponse response = s3Client.putObject(putOb, RequestBody.fromBytes(getObjectFile(objectPath)));
//            return response.eTag();
//        } catch (S3Exception e) {
//            System.err.println(e.getMessage());
//            System.exit(1);
//        }
//        return "";
//    }
//
//    private byte[] getObjectFile(String filePath) {
//        FileInputStream fileInputStream = null;
//        byte[] bytesArray = null;
//        try {
//            File file = new File(filePath);
//            bytesArray = new byte[(int) file.length()];
//            fileInputStream = new FileInputStream(file);
//            fileInputStream.read(bytesArray);
//        } catch (IOException e) {
//            e.printStackTrace();
//        } finally {
//            if (fileInputStream != null) {
//                try {
//                    fileInputStream.close();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        }
//        return bytesArray;
//    }
//
//    private S3Presigner getS3Presigner() {
//        return  S3Presigner.builder()
//                .region(Region.of(awsProperties.getRegion()))
//                .credentialsProvider(getAwsCredentialsProviderChain())
//                .build();
//    }
//
//    /**
//     * 预览 有效时间为1小时
//     *
//     * @param key
//     * @return
//     */
//    public String preview(String bucketName, String key) {
//        GetObjectRequest getObjectRequest =
//                GetObjectRequest.builder()
//                        .bucket(bucketName)
//                        .key(key)
//                        .build();
//
//        GetObjectPresignRequest getObjectPresignRequest =  GetObjectPresignRequest.builder()
//                .signatureDuration(Duration.ofMinutes(60))
//                .getObjectRequest(getObjectRequest)
//                .build();
//
//        PresignedGetObjectRequest presignedGetObjectRequest =
//                getS3Presigner().presignGetObject(getObjectPresignRequest);
//
//        return presignedGetObjectRequest.url().toString();
//    }
//
//}
