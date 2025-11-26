package com.example.nexusai.controller;
import com.example.nexusai.domain.Dataset;
import com.example.nexusai.mapper.DatasetMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.xml.crypto.Data;
import java.util.*;

@RestController
public class TestController {
    @Autowired
    private DatasetMapper datasetMapper;

    @GetMapping("/api/test/testDB")
    public List<Dataset> testDB(){

        return datasetMapper.selectList(null);
    }
}