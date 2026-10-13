package com.example.demo.system.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("`user`")
public class User {
    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String name;

    private String account;
    private String passwd;
}
