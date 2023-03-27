package me.test.minio.service.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import me.test.minio.configurer.oss.MinioTemplate;
import me.test.minio.service.UploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * @author test
 * @date 2021/3/23
 */
@Service
@RequiredArgsConstructor
public class UploadServiceImpl implements UploadService {

    @Autowired
    MinioTemplate minioTemplate;

    @Override
    public Map<String, Object> initMultiPartUpload(String path, String filename, Integer partCount, String contentType) {
        path = path.replaceAll("/+", "/");
        if (path.indexOf("/") == 0) {
            path = path.substring(1);
        }
        String filePath = path + "/" + filename;

        Map<String, Object> result;
        // TODO::单文件上传可拆分，这里只做演示，可直接上传完成
        if (partCount == 1) {
            String uploadObjectUrl = minioTemplate.getUploadObjectUrl(filePath);
            result = ImmutableMap.of("uploadUrls", ImmutableList.of(uploadObjectUrl));
        } else {
            result = minioTemplate.initMultiPartUpload(filePath, partCount, contentType);
        }

        return result;
    }

    @Override
    public boolean mergeMultipartUpload(String objectName, String uploadId) {
        return minioTemplate.mergeMultipartUpload(objectName, uploadId);
    }
}
