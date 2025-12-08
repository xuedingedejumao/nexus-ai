package com.example.nexusai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.nexusai.entity.ChatHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatHistoryMapper extends BaseMapper<ChatHistory> {
}
