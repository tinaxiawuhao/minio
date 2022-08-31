package me.test.minio.s3.v1;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.s3.transfer.*;
import com.amazonaws.services.s3.transfer.internal.S3ProgressListener;
import com.amazonaws.services.s3.transfer.model.UploadResult;
import com.amazonaws.util.StringInputStream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import me.test.minio.s3.v1.config.AwsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static me.test.minio.s3.v1.BucketAndObjectValidator.*;

/**
 * @author test
 * @date 2021/3/23
 */
@Component
@Configuration
@EnableConfigurationProperties({AwsProperties.class})
@Slf4j
public class S3Utils {

    private final AwsProperties awsProperties;

    private AmazonS3 s3Client;

    public S3Utils(AwsProperties awsProperties) {
        this.awsProperties = awsProperties;
    }

    @PostConstruct
    public void init() {
        AmazonS3ClientBuilder builder =  AmazonS3Client.builder();
                // set endpoint
        builder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(awsProperties.getEndPoint(), awsProperties.getRegion()));
                // set credentials
        builder.setCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(awsProperties.getAccessKey(), awsProperties.getSecretKey())));
                // path-style bucket naming is highly recommended
        builder.setPathStyleAccessEnabled(true);
        s3Client = builder.build();
    }

    /**
     * 创建桶
     * @param bucketName
     */
    @SneakyThrows
    public void createBucket(String bucketName) {
        if(checkBucketExistence(s3Client, bucketName)){
            // create the bucket - used for subsequent demo operations
            s3Client.createBucket(bucketName);
        }

    }

    /**
     * 清空并且删除桶
     * @param bucketName
     */
    @SneakyThrows
    public void emptyAndDeleteBucket(String bucketName) {
        if(checkBucketExistence(s3Client, bucketName)){
            // delete all bucket content
            if (BucketVersioningConfiguration.OFF.equals(s3Client.getBucketVersioningConfiguration(bucketName).getStatus())) {
                // no versioning, so delete all objects
                for (S3ObjectSummary summary : s3Client.listObjects(bucketName).getObjectSummaries()) {
                    System.out.println(String.format("Deleting object [%s/%s]", bucketName, summary.getKey()));
                    s3Client.deleteObject(bucketName, summary.getKey());
                }
            } else {
                // versioning was enabled, so delete all versions
                for (S3VersionSummary summary : s3Client.listVersions(bucketName, null).getVersionSummaries()) {
                    System.out.println(String.format("Deleting version [%s/%s/%s]", bucketName, summary.getKey(), summary.getVersionId()));
                    s3Client.deleteVersion(bucketName, summary.getKey(), summary.getVersionId());
                }
            }

            // delete the bucket
            s3Client.deleteBucket(bucketName);
        }

    }

    /**
     * 上传对象
     * @param bucketName
     * @param key
     * @param content
     */
    @SneakyThrows
    public void createObject(String bucketName, String key, final String content) {
        bucketName = Optional.ofNullable(bucketName).orElse(awsProperties.getBucket());
        //自动创建桶
        createBucket(bucketName);
        checkObjectExistence(s3Client, bucketName, key);
        //创建元数据对象
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(content.length());
        s3Client.putObject(bucketName, key, new StringInputStream( content ), metadata);
    }

    /**
     * 上传对象(默认桶)
     * @param key
     * @param file
     */
    @SneakyThrows
    public void createObject(String key, final MultipartFile file) {
        createObject(awsProperties.getBucket(),key,file);
    }

    /**
     * 上传对象(选择桶)
     * @param bucketName
     * @param key
     * @param file
     */
    @SneakyThrows
    public void createObject(String bucketName, String key, final MultipartFile file) {
        bucketName = Optional.ofNullable(bucketName).orElse(awsProperties.getBucket());
        //自动创建桶
        createBucket(bucketName);
        checkObjectExistence(s3Client, awsProperties.getBucket(), key);
        //创建元数据对象
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        s3Client.putObject(awsProperties.getBucket(), key, file.getInputStream(), metadata);
    }

    /**
     * 读取对象信息
     * @param bucketName
     * @param key
     */
    @SneakyThrows
    public void readObject(String bucketName, String key) {
        bucketName = Optional.ofNullable(bucketName).orElse(awsProperties.getBucket());
        if(checkBucketExistence(s3Client, bucketName)){
            // read the object from the demo bucket
            S3Object object = s3Client.getObject(bucketName, key);
            //流式获取文件内容
            BufferedReader reader = new BufferedReader(new InputStreamReader(object.getObjectContent()));
            while (true) {
                String line;
                try {
                    line = reader.readLine();
                    if (line == null) break;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * 读取对象信息(默认桶)
     * @param key
     */
    @SneakyThrows
    public void readObject(String key) {
        readObject(awsProperties.getBucket(),key);

    }


    /**
     * 更新对象
     * @param bucketName
     * @param key
     * @param file
     */
    @SneakyThrows
    public void updateObject(String bucketName, String key, final MultipartFile file) {
        bucketName = Optional.ofNullable(bucketName).orElse(awsProperties.getBucket());
        if(checkBucketExistence(s3Client, bucketName)){
            checkObjectContent(s3Client, bucketName, key);
            s3Client.putObject(bucketName, key, file.getInputStream(), null);
        }

    }

    /**
     * 更新对象(默认桶）
     * @param key
     * @param file
     */
    @SneakyThrows
    public void updateObject(String key, final MultipartFile file) {
        updateObject(awsProperties.getBucket(),key,file);

    }

    /**
     * 删除对象
     * @param bucketName
     * @param key
     */
    @SneakyThrows
    public void deleteObject(String bucketName, String key) {
        bucketName = Optional.ofNullable(bucketName).orElse(awsProperties.getBucket());
        if(checkBucketExistence(s3Client, bucketName)){
            checkObjectExistence(s3Client, bucketName, key);
            s3Client.deleteObject(bucketName, key);
        }

    }

    /**
     * 删除对象(默认桶)
     * @param key
     */
    @SneakyThrows
    public void deleteObject(String key) {
        deleteObject(awsProperties.getBucket(),key);

    }

    /**
     * 上传大文件
     * @param bucketName
     * @param key
     * @param filePath
     */
    @SneakyThrows
    public void createLargeObject( String bucketName, String key, String filePath) {
        bucketName = Optional.ofNullable(bucketName).orElse(awsProperties.getBucket());
        checkObjectMetadata(s3Client, bucketName, key);

        TransferManager transferManager = TransferManagerBuilder.standard()
                .withS3Client(s3Client)
                .build();
        Upload upload = transferManager.upload(bucketName, key, new File(filePath));
        while (!upload.isDone()) {
            System.out.println("Upload state: " + upload.getState().toString());
            System.out.println("Percent transferred: " + upload.getProgress().getPercentTransferred());
            Thread.sleep(1000);
        }
        transferManager.shutdownNow(false);
    }

    /**
     * 分段上传文件至S3
     * @param bucketName
     * @param key
     * @param multipartFile
     */
    public void uploadMultipartFileByPart(String bucketName, String key, final MultipartFile multipartFile) {

        //声明线程池
        ExecutorService exec = Executors.newFixedThreadPool(3);
        long size = multipartFile.getSize();

        // 得到总共的段数，和 分段后，每个段的开始上传的字节位置
        List<Long> positions = Collections.synchronizedList(new ArrayList<>());
        long filePosition = 0;
        while (filePosition < size) {
            positions.add(filePosition);
            filePosition += Math.min(awsProperties.getMinPartSize(), (size - filePosition));
        }
        log.info("总大小：{}，分为{}段", size, positions.size());
        // 创建一个列表保存所有分传的 PartETag, 在分段完成后会用到
        List<PartETag> partETags = Collections.synchronizedList(new ArrayList<>());
        // 第一步，初始化，声明下面将有一个 Multipart Upload
        InitiateMultipartUploadRequest initRequest = new InitiateMultipartUploadRequest(bucketName, key);
        InitiateMultipartUploadResult initResponse = s3Client.initiateMultipartUpload(initRequest);
        log.info("开始上传");
        long begin = System.currentTimeMillis();
        try {
            // MultipartFile 转 File
            File toFile = multipartFileToFile(multipartFile);
            for (int i = 0; i < positions.size(); i++) {
                int finalI = i;
                exec.execute(() -> {
                    long time1 = System.currentTimeMillis();
                    UploadPartRequest uploadRequest = new UploadPartRequest()
                            .withBucketName(bucketName)
                            .withKey(key)
                            .withUploadId(initResponse.getUploadId())
                            .withPartNumber(finalI + 1)
                            .withFileOffset(positions.get(finalI))
                            .withFile(toFile)
                            .withPartSize(Math.min(awsProperties.getMinPartSize(), (size - positions.get(finalI))));
                    // 第二步，上传分段，并把当前段的 PartETag 放到列表中
                    partETags.add(s3Client.uploadPart(uploadRequest).getPartETag());
                    long time2 = System.currentTimeMillis();
                    log.info("第{}段上传耗时：{}", finalI + 1, (time2 - time1));
                });
            }
            //任务结束关闭线程池
            exec.shutdown();
            //判断线程池是否结束，不加会直接结束方法
            while (true) {
                if (exec.isTerminated()) {
                    break;
                }
            }
            // 第三步，完成上传，合并分段
            CompleteMultipartUploadRequest compRequest = new CompleteMultipartUploadRequest(bucketName, key,
                    initResponse.getUploadId(), partETags);
            s3Client.completeMultipartUpload(compRequest);
            //删除本地缓存文件
            toFile.delete();
        } catch (Exception e) {
            s3Client.abortMultipartUpload(new AbortMultipartUploadRequest(bucketName, key, initResponse.getUploadId()));
            log.error("Failed to upload, " + e.getMessage());
        }
        long end = System.currentTimeMillis();
        log.info("总上传耗时：{}", (end - begin));
    }

    /**
     * MultipartFile 转 File
     */
    @SneakyThrows
    private File multipartFileToFile(MultipartFile file){
        InputStream ins = file.getInputStream();
        File toFile = new File(Objects.requireNonNull(file.getOriginalFilename()));
        //获取流文件
        OutputStream os = new FileOutputStream(toFile);
        int bytesRead = 0;
        byte[] buffer = new byte[8192];
        while ((bytesRead = ins.read(buffer, 0, 8192)) != -1) {
            os.write(buffer, 0, bytesRead);
        }
        os.close();
        ins.close();
        return toFile;
    }

    /**
     * 下载大文件
     * @param bucketName
     * @param key
     * @param fileNamePrefix
     */
    @SneakyThrows
    public void downloadLargeFile(String bucketName, String key, String fileNamePrefix) {
        bucketName = Optional.ofNullable(bucketName).orElse(awsProperties.getBucket());
        checkObjectMetadata(s3Client, bucketName, key);

        // file will be placed in temp dir with .tmp extension
        File file = File.createTempFile(fileNamePrefix, null);

        TransferManager transferManager = TransferManagerBuilder.standard()
                .withS3Client(s3Client)
                .build();

        // download the object to file
        Download download = transferManager.download(bucketName, key, file);

        while (!download.isDone()) {
            log.info("Download state: " + download.getState().toString());
            log.info("Percent transferred: " + download.getProgress().getPercentTransferred());
            Thread.sleep(1000);
        }
        transferManager.shutdownNow(false);
        log.info("Download is finished, content is in the following file.");
        log.info(file.getAbsolutePath());
    }

    /**
     * 获取对象列表
     * @param bucketName
     */
    public ObjectListing listObjects(String bucketName) {
        bucketName = Optional.ofNullable(bucketName).orElse(awsProperties.getBucket());
        ObjectListing objectListing = s3Client.listObjects(bucketName);
        checkListing( "ListObjects", objectListing.getObjectSummaries(), bucketName );
        return objectListing;
    }

    /**
     * 获取对象列表(默认桶)
     */
    public ObjectListing listObjects() {
        return listObjects(awsProperties.getBucket());
    }

    /**
     * @param operation
     * @param objectSummaries
     * @param bucketName
     */
    private static void checkListing(String operation, List<S3ObjectSummary> objectSummaries, String bucketName) {
        log.info(operation + " found " + objectSummaries.size() + " objects in " + bucketName);
        for (S3ObjectSummary objectSummary : objectSummaries) {
            log.info(objectSummary.getKey());
        }
    }

    /**
     * 获取对象列表分页
     * @param bucketName
     */
    @SneakyThrows
    public List<String> listObjectsByPages(String bucketName) {
        bucketName = Optional.ofNullable(bucketName).orElse(awsProperties.getBucket());
        List<String> keys = new ArrayList<String>();
        int pages = 1;

        ListObjectsRequest listObjectsRequest = new ListObjectsRequest();
        listObjectsRequest.setBucketName(bucketName);
        listObjectsRequest.setMaxKeys(1);
        ObjectListing objectListing = s3Client.listObjects(listObjectsRequest);
        addKeys( keys, objectListing.getObjectSummaries() );
        while (objectListing.isTruncated()) {
            ++pages;
            listObjectsRequest.setMarker( keys.get( keys.size() - 1 ) );
            objectListing = s3Client.listObjects(listObjectsRequest);
            addKeys( keys, objectListing.getObjectSummaries() );
        }

        checkKeyListing( "ListObjects", keys, pages, bucketName );
        return keys;
    }

    /**
     * @param keys
     * @param objectSummaries
     */
    private void addKeys(List<String> keys, List<S3ObjectSummary> objectSummaries) {
        for (S3ObjectSummary objectSummary : objectSummaries) {
            keys.add(objectSummary.getKey());
        }
    }

    /**
     * @param operation
     * @param keys
     * @param pages
     * @param bucketName
     */
    private void checkKeyListing(String operation, List<String> keys, int pages, String bucketName) {
        log.info(operation + " found " + keys.size() + " objects on " + pages + " pages in " + bucketName);
        for (String key : keys) {
            log.info(key);
        }
    }

}
