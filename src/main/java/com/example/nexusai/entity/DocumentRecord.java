package com.example.nexusai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField; // 必须导入
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.nexusai.enums.DocStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("document_record")
public class DocumentRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("filename")
    private String filename;

    @TableField("minio_url")
    private String minioUrl;

    private DocStatus status;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    @TableField("error_msg")
    private String errorMsg;
}