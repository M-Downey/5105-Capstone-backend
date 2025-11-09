package com.example.mapper;

import com.example.domain.Chat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMapper {
    void insert(Chat chat);
    Chat findById(@Param("id") Long id);
    List<Chat> listByUser(@Param("userId") Long userId);
}








