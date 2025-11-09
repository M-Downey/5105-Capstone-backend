package com.example.mapper;

import com.example.domain.Document;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocumentMapper {
    void insert(Document doc);
    int deleteById(@Param("id") Long id);
    Document findById(@Param("id") Long id);
    List<Document> listAll();
}








