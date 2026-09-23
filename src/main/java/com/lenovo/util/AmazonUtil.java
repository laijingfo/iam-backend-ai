package com.lenovo.util;

import cn.hutool.core.date.DatePattern;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import com.lenovo.entity.S3File;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.DateUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : wugc3
 * @date : 2023/6/30
 * @description : 亚马逊文件上传下载工具
 */
@Slf4j
@Component
public class AmazonUtil {

    /**
     * access_key_id
     */
    @Value("${amazon.accessKey}")
    private String ACCESS_KEY;

    /**
     * secret_key
     */
    @Value("${amazon.secretKey}")
    private String SECRET_KEY;

    /**
     * end_point
     */
    @Value("${amazon.endpoint}")
    public static String END_POINT = "https://oss2.xcloud.lenovo.com:10443";

    /**
     * 存储桶名称
     */
    private static final String BUCKET_NAME = "itsc-active-user";

    /**
     * s3client
     */
    private static volatile AmazonS3 s3Client;

    private static final String PREFIX = "public";
    private static final String LINE = "_";
    private static final String DATE_FORMAT = "/yyyy/MM/dd/";


    public AmazonUtil(@Value("${amazon.secretKey}") String sk, @Value("${amazon.accessKey}")
    String ak, @Value("${amazon.endpoint}") String endpoint) {
        END_POINT = endpoint;
        ACCESS_KEY = ak;
        SECRET_KEY = sk;
        if (s3Client == null) {
            synchronized (AmazonS3.class) {
                if (s3Client == null) {
                    ClientConfiguration config = new ClientConfiguration();

                    // S3SignerType: 使用v2版本签名，url有效期支持2年
                    // AWSS3V4SignerType: 使用v4版本签名，url有效期最大支持7天
                    config.setSignerOverride("S3SignerType");

                    AWSCredentials credentials = new BasicAWSCredentials(ACCESS_KEY, sk);
                    // region
                    s3Client = AmazonS3ClientBuilder.standard()
                            .withCredentials(new AWSStaticCredentialsProvider(credentials))
                            .withClientConfiguration(config)
                            //设置服务器所属地区
                            .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(END_POINT, Regions.CN_NORTH_1.getName()))
                            .enablePathStyleAccess()
                            .build();
                }
            }
        }
    }

    public static Bucket createNewBucket(String bucketName) throws Exception {
        Bucket b = null;
        if (s3Client.doesBucketExistV2(bucketName)) {
            System.out.format("Bucket %s already exists.\n", bucketName);
            throw new Exception("bucketName already exists");
//            return b;
        } else {
            try {
                b = s3Client.createBucket(bucketName);
            } catch (AmazonS3Exception e) {
                System.err.println(e.getErrorMessage());
            }
        }
        return b;
    }

    public static List<Bucket> listBucket() throws Exception {
        try {
            return s3Client.listBuckets();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * file upload
     *
     * @param file file
     * @return string
     */
    public static Map<String, String> uploadFile(MultipartFile file) throws Exception {
        try (InputStream inputStream = file.getInputStream()) {
            return upload(inputStream, file.getOriginalFilename(), file.getSize());
        }
    }

    public static Map<String, String> uploadFile(byte[] content, String filename) throws Exception {
        try (InputStream inputStream = new ByteArrayInputStream(content)) {
            return upload(inputStream, filename, content.length);
        }
    }

    private static Map<String, String> upload(InputStream inputStream, String filename, long contentLength) throws Exception {
        Map<String, String> result = new HashMap<>();
        String key;
        URL url;
        try {
            key = getKey(filename);
            // 设置文件上传对象
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(contentLength);
            // 判断 Bucket 是否存在，如果不存在则创建
            if (!s3Client.doesBucketExistV2(BUCKET_NAME)) {
                s3Client.createBucket(BUCKET_NAME);
            }
            PutObjectRequest request = new PutObjectRequest(BUCKET_NAME, key, inputStream, metadata);
            request.withCannedAcl(CannedAccessControlList.PublicRead);
            // 上传文件
            s3Client.putObject(request);
            // 62208000 is two years in seconds (60 * 60 * 24 * 365 * 2)
            final Date expiration = new Date(System.currentTimeMillis() + 62208000L * 1000);
            // 返回 文件url
            GeneratePresignedUrlRequest request1 = new GeneratePresignedUrlRequest(BUCKET_NAME, key)
                    .withExpiration(expiration);
            url = s3Client.generatePresignedUrl(request1);

        } catch (Exception e) {
            log.error("文件上传异常:{}{}", e.getMessage(), e);
            throw new Exception("文件上传异常");
        }

        result.put("key", key);
        result.put("url", url.toString());
        return result;
    }

    public static S3File uploadFile(MultipartFile file, String bucketName,Integer id) throws Exception {

        Map<String, String> result = new HashMap<>();
        String key;
        URL url;
        try (InputStream inputStream = file.getInputStream()) {
            key = getKey(file.getOriginalFilename());
            // 设置文件上传对象
            ObjectMetadata metadata = new ObjectMetadata();
            // 判断 Bucket 是否存在，如果不存在则创建
            if (!s3Client.doesBucketExistV2(bucketName)) {
                s3Client.createBucket(bucketName);
            }
            PutObjectRequest request = new PutObjectRequest(bucketName, key, inputStream, metadata);
            request.withCannedAcl(CannedAccessControlList.PublicRead);
            // 上传文件
            s3Client.putObject(request);

        } catch (Exception e) {
            log.error("文件上传异常:{}{}", e.getMessage(), e);
            throw new Exception("文件上传异常");
        }
        LocalDateTime currentDate = LocalDateTime.now();
        S3File s3File = new S3File();
        s3File.setKey(key);
        s3File.setBucketName(bucketName);
        s3File.setId(id);
//        s3File.setCreateTime(currentDate);
        return s3File;

    }

    /**
     * local file upload
     *
     * @param filePath filePath
     * @return string
     */
    public static String uploadFile(String filePath) throws Exception {
        if (StringUtils.isBlank(filePath)) {
            throw new Exception("上传文件不能为空");
        }

        String key;
        try {
            File file = FileUtils.getFile(filePath);
            key = getKey(file.getName());
            // 设置文件上传对象
            PutObjectRequest request = new PutObjectRequest(BUCKET_NAME, key, file);
            request.withCannedAcl(CannedAccessControlList.PublicRead);
            // 上传文件
            s3Client.putObject(request);
        } catch (Exception e) {
            log.error("文件上传异常:{}{}", e.getMessage(), e);
            throw new Exception("文件上传异常");
        }
        return END_POINT + key;
    }

    /**
     * download file
     *
     * @param key 文件的key
     * @return string
     */
    public static String downloadFile(String key) throws Exception {
        try {
            if (StringUtils.isBlank(key)) {
                return key;
            }
            GeneratePresignedUrlRequest httpRequest = new GeneratePresignedUrlRequest(BUCKET_NAME, key);
            return s3Client.generatePresignedUrl(httpRequest).toString();
        } catch (Exception e) {
            log.error("下载异常:{}{}", e.getMessage(), e);
            throw new Exception("下载异常");
        }
    }

    /**
     * del file
     *
     * @param key 文件链接
     */
    public static void delFile(String key) {
        s3Client.deleteObject(BUCKET_NAME, key);
    }

    /**
     * 生成key  例如：/public/2023/06/30/xxx.xlsx
     *
     * @param fileName 文件名称
     * @return String
     */
    public static String getKey(String fileName) {
        String dataStr = cn.hutool.core.date.DateUtil.format(new Date(), DatePattern.PURE_DATETIME_PATTERN);
        return PREFIX + DateUtils.formatDate(new Date(), DATE_FORMAT) + dataStr + "/" + fileName;
    }

    public static String genSignedUrl(String bucketName, String key) {
        final Date expiration = new Date(System.currentTimeMillis() + 62208000L * 1000);

        GeneratePresignedUrlRequest request1 = new GeneratePresignedUrlRequest(bucketName, key)
                .withExpiration(expiration);
        return s3Client.generatePresignedUrl(request1).toString();
    }
}
