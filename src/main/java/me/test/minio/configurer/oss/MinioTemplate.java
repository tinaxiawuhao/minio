package me.test.minio.configurer.oss;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.google.common.collect.HashMultimap;
import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.Bucket;
import io.minio.messages.Item;
import io.minio.messages.Part;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import me.test.minio.configurer.redis.RedisUtil;
import me.test.minio.util.Dates;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@AllArgsConstructor
public class MinioTemplate {

    /**
     * redis 工具类
     */
    RedisUtil redisUtil;

    /**
     * MinIO 客户端
     */
    CustomMinioClient minioClient;

    /**
     * MinIO 配置类
     */
    OssProperties ossProperties;

    /**
     * 初始化默认存储桶
     */
    @PostConstruct
    public void initDefaultBucket() {
        String defaultBucketName = ossProperties.getDefaultBucketName();
        if (bucketExists(defaultBucketName)) {
            log.info("默认存储桶已存在");
        } else {
            log.info("创建默认存储桶");
            makeBucket(ossProperties.getDefaultBucketName());
        }
        ;
    }

    /**
     * 查询所有存储桶
     *
     * @return Bucket 集合
     */
    @SneakyThrows
    public List<Bucket> listBuckets() {
        return minioClient.listBuckets();
    }

    /**
     * 桶是否存在
     *
     * @param bucketName 桶名
     * @return 是否存在
     */
    @SneakyThrows
    private boolean bucketExists(String bucketName) {
        return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
    }

    /**
     * 创建存储桶
     *
     * @param bucketName 桶名
     */
    @SneakyThrows
    public void makeBucket(String bucketName) {
        if (!bucketExists(bucketName)) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());

        }
    }

    /**
     * 删除一个空桶 如果存储桶存在对象不为空时，删除会报错。
     *
     * @param bucketName 桶名
     */
    @SneakyThrows
    public void removeBucket(String bucketName) {
        minioClient.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
    }



    /**
     * 上传文件
     *
     * @param inputStream      流
     * @param originalFileName 原始文件名
     * @param bucketName       桶名
     * @return OssFile
     */
    @SneakyThrows
    public OssFile putObject(InputStream inputStream, String bucketName, String originalFileName) {
        String uuidFileName = generateOssUuidFileName(originalFileName);
        try {
            if (StrUtil.isEmpty(bucketName)) {
                bucketName = ossProperties.getDefaultBucketName();
            }
            minioClient.putObject(
                    PutObjectArgs.builder().bucket(bucketName).object(uuidFileName).stream(
                            inputStream, inputStream.available(), -1)
                            .build());
            return new OssFile(uuidFileName, originalFileName);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    /**
     * 返回临时带签名、过期时间一天、Get请求方式的访问URL
     *
     * @param bucketName  桶名
     * @param ossFilePath Oss文件路径
     * @return
     */
    @SneakyThrows
    public String getPresignedObjectUrl(String bucketName, String ossFilePath) {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucketName)
                        .object(ossFilePath)
                        .expiry(60 * 60 * 24)
                        .build());
    }

    /**
     * GetObject接口用于获取某个文件（Object）。此操作需要对此Object具有读权限。
     *
     * @param bucketName  桶名
     * @param ossFilePath Oss文件路径
     */
    @SneakyThrows
    public InputStream getObject(String bucketName, String ossFilePath) {
        return minioClient.getObject(
                GetObjectArgs.builder().bucket(bucketName).object(ossFilePath).build());
    }

    /**
     * 查询桶的对象信息
     *
     * @param bucketName 桶名
     * @param recursive  是否递归查询
     * @return
     */
    @SneakyThrows
    public Iterable<Result<Item>> listObjects(String bucketName, boolean recursive) {
        return minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucketName).recursive(recursive).build());
    }

	/**
     * 生成随机文件名，防止重复
     *
     * @param originalFilename 原始文件名
     * @return 
     */
    private String generateOssUuidFileName(String originalFilename) {
        return "files" + StrUtil.SLASH + DateUtil.format(new Date(), "yyyy-MM-dd") + StrUtil.SLASH + UUID.randomUUID() + StrUtil.SLASH + originalFilename;
    }

    /**
     * 获取带签名的临时上传元数据对象，前端可获取后，直接上传到Minio
     *
     * @param bucketName
     * @param fileName
     * @return
     */
    @SneakyThrows
    public Map<String, String> getPresignedPostFormData(String bucketName, String fileName) {
        // 为存储桶创建一个上传策略，过期时间为7天
        PostPolicy policy = new PostPolicy(bucketName, ZonedDateTime.now().plusDays(7));
        // 设置一个参数key，值为上传对象的名称
        policy.addEqualsCondition("key", fileName);
        // 添加Content-Type以"image/"开头，表示只能上传照片
        policy.addStartsWithCondition("Content-Type", "image/");
        // 设置上传文件的大小 64kiB to 10MiB.
        policy.addContentLengthRangeCondition(64 * 1024, 10 * 1024 * 1024);
        return minioClient.getPresignedPostFormData(policy);
    }

    /**
     * 单文件签名上传
     *
     * @param objectName 文件全路径名称
     * @return /
     */
    @SneakyThrows
    public String getUploadObjectUrl(String objectName) {
        // 上传文件时携带content-type头即可
        HashMultimap<String, String> headers = HashMultimap.create();
        headers.put("Content-Type", "application/octet-stream");
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.PUT)
                        .bucket(ossProperties.getDefaultBucketName())
                        .object(objectName)
                        .expiry(1, TimeUnit.DAYS)
                        .extraHeaders(headers)
                        .build()
        );
    }


    /**
     *  初始化分片上传
     *
     * @param objectName 文件全路径名称
     * @param partCount 分片数量
     * @param contentType 类型，如果类型使用默认流会导致无法预览
     * @return /
     */
    @SneakyThrows
    public Map<String, Object> initMultiPartUpload(String objectName, int partCount, String contentType) {
        JSONObject result = new JSONObject(true);
        if (StrUtil.isBlank(contentType)) {
            contentType = "application/octet-stream";
        }
        HashMultimap<String, String> headers = HashMultimap.create();
        headers.put("Content-Type", contentType);
        String uploadId = minioClient.initMultiPartUpload(ossProperties.getDefaultBucketName(), null, objectName, headers, null);
        result.putOnce("uploadId", uploadId);

        //将临时文件夹存储到redis
        String concat = generateOssUuidFileName(objectName);
        result.putOnce("folderId", concat);
        redisUtil.set(uploadId,concat,60*60*24*7);
        JSONArray partList = new JSONArray();
        //请求参数
        Map<String, String> reqParams = new HashMap<>();
        //reqParams.put("response-content-type", "application/json");
        reqParams.put("uploadId", uploadId);
        for (int i = 1; i <= partCount; i++) {
            JSONObject uploadInfo = new JSONObject(true);
            reqParams.put("partNumber", String.valueOf(i));
            String uploadUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(ossProperties.getDefaultBucketName())
                            .object(concat)
                            .expiry(1, TimeUnit.DAYS)
                            .extraQueryParams(reqParams)
                            .build());
            uploadInfo.putOnce("part",i);
            uploadInfo.putOnce("uploadUrl",uploadUrl);
            partList.add(uploadInfo);
        }
        result.putOnce("uploadUrls", partList);
        return result;
    }

    /**
     * 分片上传完后合并
     *
     * @param objectName 文件全路径名称
     * @param uploadId 返回的uploadId
     * @return /
     */
    public boolean mergeMultipartUpload(String objectName, String uploadId) {
        try {
            //TODO::目前仅做了最大1000分片
            Part[] parts = new Part[1000];
            ListPartsResponse partResult = minioClient.listMultipart(ossProperties.getDefaultBucketName(), null, objectName, 1000, 0, uploadId, null, null);
            int partNumber = 1;
            for (Part part : partResult.result().partList()) {
                parts[partNumber - 1] = new Part(partNumber, part.etag());
                partNumber++;
            }
            minioClient.mergeMultipartUpload(ossProperties.getDefaultBucketName(), null, objectName, uploadId, parts, null, null);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    /**
     * 初始化分片信息后，获取对应的分片地址信息
     * @param folderId
     * @param part
     * @return
     */
    public String getAllFilePartInfo(String folderId,int part) {
        if(redisUtil.hasKey(folderId)){
            JSONObject jsonObject= (JSONObject) redisUtil.get(folderId);
            JSONArray array= (JSONArray) jsonObject.get("uploadUrls");
            return (String) ((JSONObject)array.get(part)).get("uploadUrl");
        }
        return null;
    }

    /**
     * 获取分片上传已经上传的索引列表
     * @param uploadId
     * @return
     */

    public List<Integer> listIncompleteUploads(String uploadId) {
        List<Integer> list=new ArrayList<>();
        if(redisUtil.hasKey(uploadId)){
            String concat= (String) redisUtil.get(uploadId);
            list  = listIncompleteUploads(ossProperties.getDefaultBucketName(), concat);
        }
        return list;
    }

    /**
     * 查询已经上传成功的文件
     * @param bucketName
     * @param prefix
     */
    @SneakyThrows
    public List<Integer>  listIncompleteUploads(String bucketName,String prefix){
        List<Integer> rslt=new ArrayList<>();
        Iterable<Result<Item>> results=minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(bucketName)
                        .prefix(prefix)
                        .maxKeys(1000)
                        .build());
        for (Result<Item> result : results) {
            String obectName = result.get().objectName();
            String[] split = StringUtils.split(obectName, "_");
            assert split != null;
            rslt.add(Integer.parseInt(split[split.length-1]));
        }
        return rslt;
    }
}