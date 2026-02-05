-- Fix missing columns in document_record table
ALTER TABLE document_record ADD COLUMN create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';
ALTER TABLE document_record ADD COLUMN update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';
ALTER TABLE document_record ADD COLUMN error_msg TEXT COMMENT '错误信息';

-- Fix potential missing columns in chat_history if needed (Preventive)
-- ALTER TABLE chat_history ADD COLUMN ...;
