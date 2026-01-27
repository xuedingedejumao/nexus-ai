package com.example.nexusai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.nexusai.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
