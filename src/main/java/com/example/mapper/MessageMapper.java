package com.example.mapper;

import com.example.domain.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MessageMapper {
    void insert(Message message);
    List<Message> listByChat(@Param("chatId") Long chatId);
}








