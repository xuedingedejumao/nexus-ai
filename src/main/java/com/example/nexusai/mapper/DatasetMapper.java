package com.example.nexusai.mapper;

import com.example.nexusai.domain.Dataset;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DatasetMapper extends BaseMapper<Dataset> {
    // 继承 BaseMapper，自动拥有增删改查能力
}