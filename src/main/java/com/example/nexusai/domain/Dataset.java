package com.example.nexusai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("dataset")
public class Dataset {
    @TableId(type= IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private LocalDateTime create_time;
}
