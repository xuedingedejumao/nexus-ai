package com.example.nexusai.config;

import dev.langchain4j.service.V;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class MinioConfig {

    @Value("${nexus.minio.endpoint}")
    private String endpoint;

    @Value("${nexus.minio.access-key}")
    private String accessKey;

    @Value("${nexus.minio.secret-key}")
    private String secretKey;

    @Value("${nexus.minio.bucket-name}")
    private String bucketName;

    @Bean
    public MinioClient minioClient(){
        MinioClient minio = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        try {
            boolean found = minio.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if(!found){
                minio.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Minio Bucket {} 创建成功", bucketName);
            }else{
                log.info("Minio Bucket {} 已存在", bucketName);
            }
        }catch (Exception e){
            log.error("Minio初始化Bucket失败", e);
            throw new RuntimeException(e);
        }


        return minio;
    }
}
