package me.tuine.minio.configurer;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.google.common.collect.HashMultimap;
import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.Item;
import io.minio.messages.Part;
import lombok.SneakyThrows;
import me.tuine.minio.util.CustomMinioClient;
import me.tuine.minio.util.Dates;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author tuine
 * @date 2021/3/23
 */
@Component
@Configuration
@EnableConfigurationProperties({MinioProperties.class})
public class MinIoUtils {

    @Autowired
    private RedisUtil redisUtil;

    private final MinioProperties minioProperties;
    private CustomMinioClient customMinioClient;

    public MinIoUtils(MinioProperties minioProperties) {
        this.minioProperties = minioProperties;
    }

    @PostConstruct
    public void init() {
        MinioClient minioClient = MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccesskey(), minioProperties.getSecretkey())
                .build();
        customMinioClient = new CustomMinioClient(minioClient);
    }

    /**
     * 单文件签名上传
     *
     * @param objectName 文件全路径名称
     * @return /
     */
    public String getUploadObjectUrl(String objectName) {
        // 上传文件时携带content-type头即可
        /*if (StrUtil.isBlank(contentType)) {
            contentType = "application/octet-stream";
        }
        HashMultimap<String, String> headers = HashMultimap.create();
        headers.put("Content-Type", contentType);*/
        try {
            return customMinioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(minioProperties.getBucket())
                            .object(objectName)
                            .expiry(1, TimeUnit.DAYS)
                            //.extraHeaders(headers)
                            .build()
            );
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
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
        String uploadId = customMinioClient.initMultiPartUpload(minioProperties.getBucket(), null, objectName, headers, null);
        result.putOnce("uploadId", uploadId);

        //添加当前时间戳，防止重复上传同一个文件造成数据错误
        String dateStr=Dates.now().formatAllDateTime();
        //将临时文件夹存储到redis
        String concat = dateStr.concat("/").concat(objectName);
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
            String uploadUrl = customMinioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(minioProperties.getBucket())
                            .object(concat)
                            .expiry(1, TimeUnit.DAYS)
                            .extraQueryParams(reqParams)
                            .build());
            uploadInfo.putOnce("part",i);
            uploadInfo.putOnce("uploadUrl",uploadUrl);
            partList.add(uploadInfo);
        }
        result.putOnce("uploadUrls", partList);
        redisUtil.set(concat,result);//缓存结果
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
            ListPartsResponse partResult = customMinioClient.listMultipart(minioProperties.getBucket(), null, objectName, 1000, 0, uploadId, null, null);
            int partNumber = 1;
            for (Part part : partResult.result().partList()) {
                parts[partNumber - 1] = new Part(partNumber, part.etag());
                partNumber++;
            }
            customMinioClient.mergeMultipartUpload(minioProperties.getBucket(), null, objectName, uploadId, parts, null, null);
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
            list  = listIncompleteUploads(minioProperties.getBucket(), concat);
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
        Iterable<Result<Item>> results=customMinioClient.listObjects(
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
