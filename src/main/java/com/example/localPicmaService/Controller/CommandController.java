//package com.example.localPicmaService.Controller;
//import com.example.localPicmaService.Class.SsePushService;
//import com.example.localPicmaService.cool.ExcuteCMD;
//import org.springframework.web.bind.annotation.*;
//
//@RestController
//@RequestMapping("/cmd")
//public class CommandController {
//
//    private final SsePushService ssePushService;
//
//    public CommandController(SsePushService ssePushService) {
//        this.ssePushService = ssePushService;
//    }
//
//    @PostMapping("/ping/{clientId}")
//    public String ping(@PathVariable String clientId) {
//
//        new Thread(() -> {
//            ExcuteCMD.execute("ping baidu.com", line -> {
//                ssePushService.send(clientId, line);
//            });
//            ssePushService.complete(clientId);
//        }).start();
//
//        return "started";
//    }
//}
