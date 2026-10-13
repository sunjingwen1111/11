package com.example.demo.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.system.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
