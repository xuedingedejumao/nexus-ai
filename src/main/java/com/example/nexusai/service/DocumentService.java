package com.example.nexusai.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.analysis.function.Min;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentService {
    private final MinioClient minioClient;
    private final RagService ragService;

    @Value("${nexus.minio.bucket-name}")
    private String bucketName;

    private final Tika tika = new Tika();

    public String uploadAndEmbed(MultipartFile file){
        String originFileName = file.getOriginalFilename();

        String objectName = UUID.randomUUID() + "_" + originFileName;

        try {
            InputStream inputStream = file.getInputStream();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .build()
            );
            inputStream.close();
            log.info("文件上传成功：{}", objectName);

            String content = tika.parseToString(file.getInputStream());
            log.info("文件解析成功，内容长度：{}", content.length());

            if(content.trim().isEmpty()){
                throw new RuntimeException("文件上传成功，但未提取到文字");
            }

            String finalContent = "【来源文件： " + originFileName + "】\n" + content;

            ragService.ingest(finalContent);

            return "文件上传并入库成功";
        }catch (Exception e){
            log.error("文件存储失败", e);
            throw new RuntimeException("文件存储失败: " + e.getMessage());
        }
    }

}
