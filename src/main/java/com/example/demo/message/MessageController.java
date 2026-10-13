package com.example.demo.message;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Data
@CrossOrigin
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public String receive(@RequestBody MessageRequest request) {
        System.out.println("接收到的消息：\t" + request.message);
        log.info("普通消息");
        log.warn(" 提醒 需要注意");
        log.error("异常，有问题");
        return "已接收到" + request.message();
    }

    public record MessageRequest(String message) {
    }
}
