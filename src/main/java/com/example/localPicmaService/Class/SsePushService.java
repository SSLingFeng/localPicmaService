//package com.example.localPicmaService.Class;
//
//import org.springframework.stereotype.Service;
//import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
//
//import java.io.IOException;
//import java.util.Map;
//import java.util.concurrent.ConcurrentHashMap;
//
//@Service
//public class SsePushService {
//
//    private final Map<String, SseEmitter> clients = new ConcurrentHashMap<>();
//
//    /**
//     * 建立 SSE 连接
//     */
//    public SseEmitter connect(String clientId) {
//        SseEmitter emitter = new SseEmitter(0L); // 永不超时
//
//        clients.put(clientId, emitter);
//
//        emitter.onCompletion(() -> clients.remove(clientId));
//        emitter.onTimeout(() -> clients.remove(clientId));
//        emitter.onError(e -> clients.remove(clientId));
//
//        return emitter;
//    }
//
//    /**
//     * 推送数据给指定客户端
//     */
//    public void send(String clientId, Object data) {
//        SseEmitter emitter = clients.get(clientId);
//        if (emitter == null) return;
//
//        try {
//            emitter.send(SseEmitter.event()
//                    .name("message")
//                    .data(data));
//        } catch (IOException e) {
//            clients.remove(clientId);
//        }
//    }
//
//    /**
//     * 结束连接
//     */
//    public void complete(String clientId) {
//        SseEmitter emitter = clients.remove(clientId);
//        if (emitter != null) {
//            emitter.complete();
//        }
//    }
//}
