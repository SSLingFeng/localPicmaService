//package com.example.localPicmaService.Controller;
//
//import com.example.localPicmaService.Class.SsePushService;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
//
//@RestController
//@RequestMapping("/sse")
//public class SseController {
//
//    private final SsePushService ssePushService;
//
//    public SseController(SsePushService ssePushService) {
//        this.ssePushService = ssePushService;
//    }
//
//    @GetMapping(value = "/connect/{clientId}", produces = "text/event-stream")
//    public SseEmitter connect(@PathVariable String clientId) {
//        return ssePushService.connect(clientId);
//    }
//}


/**
 * SSE请求服用，
 * 包含文件    com.../Class/SsePushService.java
 *com.../Controller/CommandController.java
 *com.../Controller/SseController.java
 *com.../cool/ExcuteCMD.java
 *
 * 通过两个接口触发持续SSE请求。
 */