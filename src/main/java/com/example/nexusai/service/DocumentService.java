package com.example.nexusai.service;

import com.example.nexusai.entity.DocumentRecord;
import com.example.nexusai.enums.DocStatus;
import com.example.nexusai.mapper.ChatHistoryMapper;
import com.example.nexusai.mapper.DocumentRecordMapper;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentService {
    private final MinioClient minioClient;
    private final DocumentRecordMapper documentRecordMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${nexus.minio.bucket-name}")
    private String bucketName;

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

            DocumentRecord documentRecord = new DocumentRecord()
                    .setFilename(originFileName)
                    .setMinioUrl(objectName)
                    .setStatus(DocStatus.PENDING);
            documentRecordMapper.insert(documentRecord);

            kafkaTemplate.send("doc-process-topic", String.valueOf(documentRecord.getId()));
            log.info(">> 生产者：文档{} 已经发送至kafka ", documentRecord.getId());

            return "文件上传成功，后台正在处理";
        }catch (Exception e){
            log.error("文件存储失败", e);
            throw new RuntimeException("文件存储失败: " + e.getMessage());
        }
    }

}
