package com.example.localPicmaService.Controller.Comic;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.crypto.digest.HmacAlgorithm;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class PicacgClient {

    private static final String BASE_URL = "https://picaapi.picacomic.com";
    private static final String API_KEY = "C69BAF41DA5ABD1FFEDC6D2FEA56B";
    private static final String SIGN_KEY = "~d}$Q7$eIni=V)9\\RK/P.RM4;9[7|@/CA}b~OW!3?EV`:<>M7pddUBL5n|0/*Cn";

    private String token;  // 登录成功后保存 token

    // ------------------- 工具方法 -------------------

    private String createSignature(String path, String nonce, String time, String method) {
        String data = (path + time + nonce + method + API_KEY).toLowerCase();
        HMac hmac = new HMac(HmacAlgorithm.HmacSHA256, SIGN_KEY.getBytes(StandardCharsets.UTF_8));
        return hmac.digestHex(data, StandardCharsets.UTF_8);
    }

    private Map<String, String> buildHeaders(String method, String path) {
        String nonce = UUID.fastUUID().toString(true); // Hutool生成32位uuid
        String time = String.valueOf(DateUtil.currentSeconds());
        String signature = createSignature(path, nonce, time, method);

        Map<String, String> headers = new HashMap<>();
        headers.put("api-key", API_KEY);
        headers.put("accept", "application/vnd.picacomic.com.v1+json");
        headers.put("app-channel", "3");
        headers.put("authorization", token == null ? "" : token);
        headers.put("time", time);
        headers.put("nonce", nonce);
        headers.put("app-version", "2.2.1.3.3.4");
        headers.put("app-uuid", "defaultUuid");
        headers.put("image-quality", "original");
        headers.put("app-platform", "android");
        headers.put("app-build-version", "45");
        headers.put("Content-Type", "application/json; charset=UTF-8");
        headers.put("user-agent", "okhttp/3.8.1");
        headers.put("version", "v1.4.1");
        headers.put("Host", "picaapi.picacomic.com");
        headers.put("signature", signature);

        return headers;
    }

    private JSONObject doGet(String path) {
        HttpResponse response = HttpRequest.get(BASE_URL + "/" + path)
                .addHeaders(buildHeaders("GET", path))
                .execute();
        if (response.getStatus() != 200) {
            throw new RuntimeException("GET 请求失败: " + response.getStatus() + " - " + response.body());
        }
        return JSONUtil.parseObj(response.body());
    }

    private JSONObject doPost(String path, Object body) {
        HttpResponse response = HttpRequest.post(BASE_URL + "/" + path)
                .addHeaders(buildHeaders("POST", path))
                .body(body == null ? "{}" : JSONUtil.toJsonStr(body))
                .execute();
        if (response.getStatus() != 200) {
            throw new RuntimeException("POST 请求失败: " + response.getStatus() + " - " + response.body());
        }
        return JSONUtil.parseObj(response.body());
    }

    // ------------------- 业务方法 -------------------

    /** 登录 */
    public void login(String email, String password) {
        JSONObject body = new JSONObject();
        body.put("email", email);
        body.put("password", password);

        JSONObject res = doPost("auth/sign-in", body);
        this.token = res.getJSONObject("data").getStr("token");
    }

    /** 获取随机漫画 */
    public JSONObject getRandomComics() {
        return doGet("comics/random");
    }

    /** 获取最新漫画 */
    public JSONObject getLatestComics(int page) {
        String path = "comics?page=" + page + "&s=dd";
        return doGet(path);
    }

    /** 获取排行榜 */
    public JSONObject getLeaderboard(String tt) {
        String path = "comics/leaderboard?tt=" + tt + "&ct=VC"; // tt=H24,D7,D30
        return doGet(path);
    }

    /** 搜索漫画 */
    public JSONObject search(String keyword, String sort, int page) {
        JSONObject body = new JSONObject();
        body.put("keyword", keyword);
        body.put("sort", sort);
        String path = "comics/advanced-search?page=" + page;
        return doPost(path, body);
    }

    /** 收藏/取消收藏 */
    public void toggleFavorite(String comicId) {
        doPost("comics/" + comicId + "/favourite", "{}");
    }

    /** 获取收藏 */
    public JSONObject getFavorites(int page, String sort) {
        String path = "users/favourite?page=" + page + "&s=" + sort;
        return doGet(path);
    }

    /** 获取漫画详情 */
    public JSONObject getComicInfo(String comicId) {
        return doGet("comics/" + comicId);
    }

    /** 获取章节列表 */
    public JSONObject getComicEpisodes(String comicId, int page) {
        return doGet("comics/" + comicId + "/eps?page=" + page);
    }

    /** 获取章节图片 */
    public JSONObject getEpisodePages(String comicId, String order, int page) {
        String path = "comics/" + comicId + "/order/" + order + "/pages?page=" + page;
        return doGet(path);
    }

    /** 点赞漫画 */
    public void likeComic(String comicId) {
        doPost("comics/" + comicId + "/like", "{}");
    }

    /** 获取漫画评论 */
    public JSONObject getComments(String comicId, int page) {
        return doGet("comics/" + comicId + "/comments?page=" + page);
    }

    /** 获取子评论 */
    public JSONObject getChildComments(String commentId, int page) {
        return doGet("comments/" + commentId + "/childrens?page=" + page);
    }

    /** 发表评论 */
    public void sendComment(String comicId, String content) {
        JSONObject body = new JSONObject();
        body.put("content", content);
        doPost("comics/" + comicId + "/comments", body);
    }

    /** 回复评论 */
    public void replyComment(String commentId, String content) {
        JSONObject body = new JSONObject();
        body.put("content", content);
        doPost("comments/" + commentId, body);
    }

    /** 点赞评论 */
    public void likeComment(String commentId) {
        doPost("comments/" + commentId + "/like", "{}");
    }
}
