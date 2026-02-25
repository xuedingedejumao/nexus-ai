package com.example.nexusai.service;

import com.example.nexusai.common.exception.NexusException;
import com.example.nexusai.entity.DocumentRecord;
import com.example.nexusai.enums.DocStatus;
import com.example.nexusai.mapper.DocumentRecordMapper;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private static final String KAFKA_TOPIC = "doc-process-topic";

    @Value("${nexus.minio.bucket-name}")
    private String bucketName;

    /**
     * 文件上传 → MinIO 存储 → 写入待处理记录 → 发送 Kafka 消息触发异步向量化。
     * Kafka 解耦使上传接口快速返回，向量化由 DocumentListener 异步消费完成。
     */
    public String uploadAndEmbed(MultipartFile file) {
        String originFileName = file.getOriginalFilename();
        String objectName = UUID.randomUUID() + "_" + originFileName;

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .build());
            log.info("文件上传至 MinIO 成功: {}", objectName);

            DocumentRecord documentRecord = new DocumentRecord()
                    .setFilename(originFileName)
                    .setMinioUrl(objectName)
                    .setStatus(DocStatus.PENDING);
            documentRecordMapper.insert(documentRecord);

            kafkaTemplate.send(KAFKA_TOPIC, String.valueOf(documentRecord.getId()));
            log.info("文档处理任务已投递至 Kafka, docId={}", documentRecord.getId());

            return "文件上传成功，后台正在处理";
        } catch (Exception e) {
            log.error("文件上传失败, filename={}", originFileName, e);
            throw new NexusException("文件上传失败: " + e.getMessage());
        }
    }
}
