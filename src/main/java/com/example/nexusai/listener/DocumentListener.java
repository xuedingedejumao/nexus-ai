package com.example.nexusai.listener;

import com.example.nexusai.entity.DocumentRecord;
import com.example.nexusai.enums.DocStatus;
import com.example.nexusai.mapper.DocumentRecordMapper;
import com.example.nexusai.service.RagService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * Kafka 文档处理消费者：从 MinIO 下载文件 → Tika 解析文本 → 向量化入库。
 * 与 DocumentService（生产者）配合，实现文件上传与向量化处理的异步解耦。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentListener {

    private final DocumentRecordMapper documentRecordMapper;
    private final MinioClient minioClient;
    private final Tika tika = new Tika();
    private final RagService ragService;

    @Value("${nexus.minio.bucket-name}")
    private String bucketName;

    @KafkaListener(topics = "doc-process-topic", groupId = "nexus-group")
    public void processDocument(String docIdStr) {
        Long docId = Long.valueOf(docIdStr);
        log.info("开始处理文档, docId={}", docId);

        updateStatus(docId, DocStatus.PROCESSING, null);

        try {
            DocumentRecord documentRecord = documentRecordMapper.selectById(docId);
            if (documentRecord == null) {
                log.error("文档记录不存在, docId={}", docId);
                return;
            }

            try (InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(documentRecord.getMinioUrl())
                            .build())) {

                String content = tika.parseToString(inputStream);
                ragService.ingest(content, documentRecord.getFilename());
            }

            updateStatus(docId, DocStatus.COMPLETED, null);
            log.info("文档处理完成, docId={}", docId);

        } catch (Exception e) {
            log.error("文档处理失败, docId={}", docId, e);
            updateStatus(docId, DocStatus.FAILED, e.getMessage());
        }
    }

    private void updateStatus(Long id, DocStatus status, String msg) {
        DocumentRecord update = new DocumentRecord()
                .setId(id)
                .setStatus(status)
                .setErrorMsg(msg);
        documentRecordMapper.updateById(update);
    }
}