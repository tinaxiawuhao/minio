package me.test.minio.configurer.oss;

import io.minio.*;
import io.minio.http.Method;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class Minio {


    /**
     * minio参数
     */
    private static final String ENDPOINT = "http://127.0.0.1:9000";
    private static final String ACCESS_KEY = "minioadmin";
    private static final String SECRET_KEY = "minioadmin";


    /**
     * 桶占位符
     */
    private static final String BUCKET_PARAM = "${bucket}";
    /**
     * bucket权限-只读
     */
    private static final String READ_ONLY = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetBucketLocation\",\"s3:ListBucket\"],\"Resource\":[\"arn:aws:s3:::" + BUCKET_PARAM + "\"]},{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::" + BUCKET_PARAM + "/*\"]}]}";
    /**
     * bucket权限-只读
     */
    private static final String WRITE_ONLY = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetBucketLocation\",\"s3:ListBucketMultipartUploads\"],\"Resource\":[\"arn:aws:s3:::" + BUCKET_PARAM + "\"]},{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:AbortMultipartUpload\",\"s3:DeleteObject\",\"s3:ListMultipartUploadParts\",\"s3:PutObject\"],\"Resource\":[\"arn:aws:s3:::" + BUCKET_PARAM + "/*\"]}]}";
    /**
     * bucket权限-读写
     */
    private static final String READ_WRITE = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetBucketLocation\",\"s3:ListBucket\",\"s3:ListBucketMultipartUploads\"],\"Resource\":[\"arn:aws:s3:::" + BUCKET_PARAM + "\"]},{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:DeleteObject\",\"s3:GetObject\",\"s3:ListMultipartUploadParts\",\"s3:PutObject\",\"s3:AbortMultipartUpload\"],\"Resource\":[\"arn:aws:s3:::" + BUCKET_PARAM + "/*\"]}]}";

    /**
     * 文件url前半段
     *
     * @param bucket 桶
     * @return 前半段
     */
    public static String getObjectPrefixUrl(String bucket) {
        return String.format("%s/%s/", ENDPOINT, bucket);
    }

    /**
     * 创建桶
     *
     * @param bucket 桶
     */
    public static void makeBucket(String bucket) throws Exception {
        MinioClient client = MinioClient.builder().endpoint(ENDPOINT).credentials(ACCESS_KEY, SECRET_KEY).build();
        // 判断桶是否存在
        boolean isExist = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!isExist) {
            // 新建桶
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    /**
     * 更新桶权限策略
     *
     * @param bucket 桶
     * @param policy 权限
     */
    public static void setBucketPolicy(String bucket, String policy) throws Exception {
        MinioClient client = MinioClient.builder().endpoint(ENDPOINT).credentials(ACCESS_KEY, SECRET_KEY).build();
        switch (policy) {
            case "read-only":
                client.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(READ_ONLY.replace(BUCKET_PARAM, bucket)).build());
                break;
            case "write-only":
                client.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(WRITE_ONLY.replace(BUCKET_PARAM, bucket)).build());
                break;
            case "read-write":
                client.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(READ_WRITE.replace(BUCKET_PARAM, bucket)).build());
                break;
            case "none":
            default:
                break;
        }
    }

    /**
     * 上传本地文件
     *
     * @param bucket    桶
     * @param objectKey 文件key
     * @param filePath  文件路径
     * @return 文件url
     */
    public static String uploadFile(String bucket, String objectKey, String filePath) throws Exception {
        MinioClient client = MinioClient.builder().endpoint(ENDPOINT).credentials(ACCESS_KEY, SECRET_KEY).build();
        client.uploadObject(UploadObjectArgs.builder().bucket(bucket).object(objectKey).filename(filePath).build());
        return getObjectPrefixUrl(bucket) + objectKey;
    }

    /**
     * 流式上传文件
     *
     * @param bucket      桶
     * @param objectKey   文件key
     * @param inputStream 文件输入流
     * @return 文件url
     */
    public static String uploadInputStream(String bucket, String objectKey, InputStream inputStream) throws Exception {
        MinioClient client = MinioClient.builder().endpoint(ENDPOINT).credentials(ACCESS_KEY, SECRET_KEY).build();
        client.putObject(PutObjectArgs.builder().bucket(bucket).object(objectKey).stream(inputStream, inputStream.available(), -1).contentType("image/png").build());
        return getObjectPrefixUrl(bucket) + objectKey;
    }

    /**
     * 下载文件
     *
     * @param bucket    桶
     * @param objectKey 文件key
     * @return 文件流
     */
    public static InputStream download(String bucket, String objectKey) throws Exception {
        MinioClient client = MinioClient.builder().endpoint(ENDPOINT).credentials(ACCESS_KEY, SECRET_KEY).build();
        return client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
    }

    /**
     * 文件复制
     *
     * @param sourceBucket    源桶
     * @param sourceObjectKey 源文件key
     * @param bucket          桶
     * @param objectKey       文件key
     * @return 新文件url
     */
    public static String copyFile(String sourceBucket, String sourceObjectKey, String bucket, String objectKey) throws Exception {
        MinioClient client = MinioClient.builder().endpoint(ENDPOINT).credentials(ACCESS_KEY, SECRET_KEY).build();
        CopySource source = CopySource.builder().bucket(sourceBucket).object(sourceObjectKey).build();
        client.copyObject(CopyObjectArgs.builder().bucket(bucket).object(objectKey).source(source).build());
        return getObjectPrefixUrl(bucket) + objectKey;
    }

    /**
     * 删除文件
     *
     * @param bucket    桶
     * @param objectKey 文件key
     */
    public static void deleteFile(String bucket, String objectKey) throws Exception {
        MinioClient client = MinioClient.builder().endpoint(ENDPOINT).credentials(ACCESS_KEY, SECRET_KEY).build();
        client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
    }

    /**
     * 获取文件签名url
     *
     * @param bucket    桶
     * @param objectKey 文件key
     * @param expires   签名有效时间  单位秒
     * @return 文件签名地址
     */
    public static String getSignedUrl(String bucket, String objectKey, int expires) throws Exception {
        MinioClient client = MinioClient.builder().endpoint(ENDPOINT).credentials(ACCESS_KEY, SECRET_KEY).build();
        return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET).bucket(bucket).object(objectKey).expiry(expires).build());
    }

    public static void main(String[] args) {
        String bucket = "public";
        String objectKey = "demo";
        try {
            // 创建桶
            makeBucket(bucket);
            // 设置桶读写权限
            setBucketPolicy(bucket, "read-write");
            // 上传本地文件
            String url1 = uploadFile(bucket, objectKey, "C:/Users/test/Desktop/API_excel_信息填写/API.xls");
            // 输出  http://127.0.0.1:9000/public/demo/123.png
            System.out.println(url1);
            // 上传文件流
            String url2 = uploadInputStream(bucket, objectKey, new FileInputStream(new File("C:/Users/test/Desktop/API_excel_信息填写/API.xls")));
            // 输出  http://127.0.0.1:9000/public/demo/123.png
            System.out.println(url2);
            // 下载文件
            InputStream inputStream = download(bucket, objectKey);
            // 文件复制
            // 创建桶
            makeBucket("test");
            String url3 = copyFile(bucket, objectKey, "test", "test.txt");
            // 输出  http://127.0.0.1:9000/test/test/abc.png
            System.out.println(url3);
            // 删除文件
            deleteFile("test", "test.txt");
            // 获取文件签名url
            String sign = getSignedUrl(bucket, objectKey, 300);
            // 该路径可访问文件五分钟
            System.out.println(sign);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}