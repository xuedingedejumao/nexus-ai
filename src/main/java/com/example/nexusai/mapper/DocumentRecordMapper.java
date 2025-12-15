package com.example.nexusai.mapper;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.nexusai.entity.DocumentRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DocumentRecordMapper extends BaseMapper<DocumentRecord> {
}
