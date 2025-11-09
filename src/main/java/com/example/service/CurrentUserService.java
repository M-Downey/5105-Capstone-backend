package com.example.service;

import com.example.domain.User;
import com.example.mapper.UserMapper;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    private final UserMapper userMapper;

    public CurrentUserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public Long requireUserIdByUsername(String username) {
        User u = userMapper.findByUsername(username);
        if (u == null || u.getId() == null) {
            throw new IllegalArgumentException("用户不存在: " + username);
        }
        return u.getId();
    }
}








