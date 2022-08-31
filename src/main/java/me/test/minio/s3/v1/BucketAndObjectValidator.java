/*
 * Copyright 2013-2018 Dell Inc. or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 * http://www.apache.org/licenses/LICENSE-2.0.txt
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package me.test.minio.s3.v1;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map.Entry;

@Slf4j
public class BucketAndObjectValidator {

    /**
     * @param s3Client
     * @param bucketName
     */
    @SneakyThrows
    protected static boolean checkBucketExistence(AmazonS3 s3Client, String bucketName) {
        boolean flag = s3Client.doesBucketExistV2(bucketName);
        String state =  flag ? "exists" : "does not exist";
        log.info(String.format("Bucket [%s] %s.", bucketName, state));
        return flag;
    }

    /**
     * @param s3Client
     * @param bucketName
     * @param key
     */
    @SneakyThrows
    protected static boolean checkObjectExistence(AmazonS3 s3Client, String bucketName, String key) {
        boolean flag = s3Client.doesObjectExist(bucketName, key);
        String state = flag ? "exists" : "does not exist";
        log.info( String.format("Object [%s/%s] %s", bucketName, key, state));
        return flag;
    }

    /**
     * @param s3Client
     * @param bucketName
     * @param key
     */
    @SneakyThrows
    protected static void checkObjectContent(AmazonS3 s3Client, String bucketName, String key) {
        try {
            S3Object object = s3Client.getObject(bucketName, key);
            BufferedReader reader = new BufferedReader(new InputStreamReader(object.getObjectContent()));
            String returnedContent = reader.readLine();
            log.info( String.format("Object [%s/%s] exists with content: [%s]", object.getBucketName(), object.getKey(), returnedContent));
        } catch (Exception e) {
            log.info( String.format("Object [%s/%s] does not exist", bucketName, key));
        }
    }

    /**
     * @param s3Client
     * @param bucketName
     * @param key
     */
    @SneakyThrows
    protected static void checkObjectMetadata(AmazonS3 s3Client, String bucketName, String key) {
        try {
            ObjectMetadata metadata = s3Client.getObjectMetadata(bucketName, key);
            log.info( String.format("Object [%s/%s] exists with system metadata:", bucketName, key));
            for (Entry<String, Object> metaEntry : metadata.getRawMetadata().entrySet()) {
                log.info( "    " + metaEntry.getKey() + " = " + metaEntry.getValue() );
            }
        } catch (Exception e) {
            log.info( String.format("Object [%s/%s] does not exist", bucketName, key));
        }
    }

}
