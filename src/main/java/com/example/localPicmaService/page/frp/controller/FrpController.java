package com.example.localPicmaService.page.frp.controller;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.localPicmaService.config.SystemConfig;
import com.example.localPicmaService.tool.SQLTool.SqlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/**
 * FRP 客户端控制端接口
 */
@RestController
@RequestMapping("/page/frp/api")
public class FrpController {

    private static final Logger log = LoggerFactory.getLogger(FrpController.class);

    @Autowired
    private SystemConfig systemConfig;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // ======================== 客户端实例管理 ========================

    @GetMapping("/client/list")
    public Map<String, Object> clientList() throws Exception {
        List<Map<String, Object>> rows = SqlUtil.query(
                "SELECT id, client_id, client_name, server_addr, server_port, login_fail_exit, "
                        + "auth_token, web_server_addr, web_server_port, web_server_user, web_server_pass, "
                        + "config_path, connect_status, last_heartbeat, remark, create_date "
                        + "FROM frp_client WHERE del_flag = 0 ORDER BY create_date",
                Map.of(), 100);

        List<Map<String, Object>> items = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new LinkedHashMap<>(row);
                item.put("has_token", row.get("auth_token") != null && !row.get("auth_token").toString().isEmpty());
                item.put("has_web_server", row.get("web_server_port") != null && ((Number) row.get("web_server_port")).intValue() > 0);
                items.add(item);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("items", items);
        result.put("frpBasePath", systemConfig.getFrpBasePath());
        return result;
    }

    @PostMapping("/client/add")
    public Map<String, Object> clientAdd(@RequestBody JSONObject body) throws Exception {
        String clientName = body.getStr("client_name");
        String serverAddr = body.getStr("server_addr");
        Integer serverPort = body.getInt("server_port");
        if (clientName == null || serverAddr == null || serverPort == null) {
            return Map.of("success", false, "error", "缺少必填参数");
        }

        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", id);
        params.put("cid", body.getStr("client_id") != null ? body.getStr("client_id") : "");
        params.put("cname", clientName);
        params.put("saddr", serverAddr);
        params.put("sport", serverPort);
        params.put("lfe", body.getBool("login_fail_exit", false));
        params.put("token", body.getStr("auth_token") != null ? body.getStr("auth_token") : "");
        params.put("wsa", body.getStr("web_server_addr") != null ? body.getStr("web_server_addr") : "127.0.0.1");
        params.put("wsp", body.getInt("web_server_port", 0));
        params.put("wsu", body.getStr("web_server_user") != null ? body.getStr("web_server_user") : "");
        params.put("wspass", body.getStr("web_server_pass") != null ? body.getStr("web_server_pass") : "");
        params.put("cfgpath", body.getStr("config_path") != null ? body.getStr("config_path") : "");
        params.put("remark", body.getStr("remark") != null ? body.getStr("remark") : "");

        SqlUtil.exec(
                "INSERT INTO frp_client (id, client_id, client_name, server_addr, server_port, login_fail_exit, "
                        + "auth_token, web_server_addr, web_server_port, web_server_user, web_server_pass, "
                        + "config_path, remark, create_date, update_date) "
                        + "VALUES ({?varchar|id?}, {?varchar|cid?}, {?varchar|cname?}, {?varchar|saddr?}, {?int|sport?}, "
                        + "{?boolean|lfe?}, {?varchar|token?}, {?varchar|wsa?}, {?int|wsp?}, {?varchar|wsu?}, "
                        + "{?varchar|wspass?}, {?varchar|cfgpath?}, {?varchar|remark?}, NOW(), NOW())",
                params);

        return Map.of("success", true, "id", id);
    }

    @PostMapping("/client/update")
    public Map<String, Object> clientUpdate(@RequestBody JSONObject body) throws Exception {
        String id = body.getStr("id");
        if (id == null) return Map.of("success", false, "error", "缺少 id");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", id);
        params.put("cid", body.getStr("client_id") != null ? body.getStr("client_id") : "");
        params.put("cname", body.getStr("client_name"));
        params.put("saddr", body.getStr("server_addr"));
        params.put("sport", body.getInt("server_port"));
        params.put("lfe", body.getBool("login_fail_exit", false));
        params.put("token", body.getStr("auth_token") != null ? body.getStr("auth_token") : "");
        params.put("wsa", body.getStr("web_server_addr") != null ? body.getStr("web_server_addr") : "127.0.0.1");
        params.put("wsp", body.getInt("web_server_port", 0));
        params.put("wsu", body.getStr("web_server_user") != null ? body.getStr("web_server_user") : "");
        params.put("wspass", body.getStr("web_server_pass") != null ? body.getStr("web_server_pass") : "");
        params.put("cfgpath", body.getStr("config_path") != null ? body.getStr("config_path") : "");
        params.put("remark", body.getStr("remark") != null ? body.getStr("remark") : "");

        SqlUtil.exec(
                "UPDATE frp_client SET client_id = {?varchar|cid?}, client_name = {?varchar|cname?}, "
                        + "server_addr = {?varchar|saddr?}, server_port = {?int|sport?}, "
                        + "login_fail_exit = {?boolean|lfe?}, auth_token = {?varchar|token?}, "
                        + "web_server_addr = {?varchar|wsa?}, web_server_port = {?int|wsp?}, "
                        + "web_server_user = {?varchar|wsu?}, web_server_pass = {?varchar|wspass?}, "
                        + "config_path = {?varchar|cfgpath?}, remark = {?varchar|remark?}, update_date = NOW() "
                        + "WHERE id = {?varchar|id?}",
                params);

        return Map.of("success", true);
    }

    @PostMapping("/client/delete")
    public Map<String, Object> clientDelete(@RequestBody JSONObject body) throws Exception {
        String id = body.getStr("id");
        if (id == null) return Map.of("success", false, "error", "缺少 id");

        // 检查是否有未删除的代理规则
        Map<String, Object> count = SqlUtil.row(
                "SELECT COUNT(*) AS cnt FROM frp_proxy WHERE client_id = {?|cid?} AND del_flag = 0",
                Map.of("cid", id));
        if (count != null && ((Number) count.get("cnt")).intValue() > 0) {
            return Map.of("success", false, "error", "该客户端下还有代理规则，请先删除代理");
        }

        SqlUtil.exec("UPDATE frp_client SET del_flag = 1, update_date = NOW() WHERE id = {?|id?}", Map.of("id", id));
        return Map.of("success", true);
    }

    // ======================== 代理规则管理 ========================

    @GetMapping("/proxy/list")
    public Map<String, Object> proxyList(@RequestParam(required = false) String clientId) throws Exception {
        String sql;
        Map<String, Object> params = new LinkedHashMap<>();

        if (clientId != null && !clientId.isEmpty()) {
            sql = "SELECT p.id, p.client_id, p.proxy_name, p.proxy_type, p.local_ip, p.local_port, "
                    + "p.remote_port, p.secret_key, p.custom_domains, p.enabled, p.remark, p.create_date, "
                    + "c.client_name "
                    + "FROM frp_proxy p LEFT JOIN frp_client c ON c.id = p.client_id "
                    + "WHERE p.del_flag = 0 AND p.client_id = {?|cid?} ORDER BY p.create_date";
            params.put("cid", clientId);
        } else {
            sql = "SELECT p.id, p.client_id, p.proxy_name, p.proxy_type, p.local_ip, p.local_port, "
                    + "p.remote_port, p.secret_key, p.custom_domains, p.enabled, p.remark, p.create_date, "
                    + "c.client_name "
                    + "FROM frp_proxy p LEFT JOIN frp_client c ON c.id = p.client_id "
                    + "WHERE p.del_flag = 0 ORDER BY c.client_name, p.create_date";
        }

        List<Map<String, Object>> rows = SqlUtil.query(sql, params, 500);

        // 解析 custom_domains
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object cd = row.get("custom_domains");
                if (cd != null && !cd.toString().isEmpty()) {
                    try {
                        row.put("custom_domains", JSONUtil.parseArray(cd.toString()).toList(String.class));
                    } catch (Exception e) {
                        row.put("custom_domains", List.of());
                    }
                } else {
                    row.put("custom_domains", List.of());
                }
            }
        }
        return Map.of("success", true, "items", rows != null ? rows : List.of());
    }

    @PostMapping("/proxy/add")
    public Map<String, Object> proxyAdd(@RequestBody JSONObject body) throws Exception {
        String clientId = body.getStr("client_id");
        String proxyName = body.getStr("proxy_name");
        String proxyType = body.getStr("proxy_type");
        Integer localPort = body.getInt("local_port");
        if (clientId == null || proxyName == null || proxyType == null || localPort == null) {
            return Map.of("success", false, "error", "缺少必填参数");
        }

        // 查重：同一客户端下代理名称不能重复
        Map<String, Object> exists = SqlUtil.row(
                "SELECT id FROM frp_proxy WHERE client_id = {?|cid?} AND proxy_name = {?|pname?} AND del_flag = 0",
                Map.of("cid", clientId, "pname", proxyName));
        if (exists != null) {
            return Map.of("success", false, "error", "代理名称「" + proxyName + "」已存在");
        }

        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        Map<String, Object> proxyParams = new LinkedHashMap<>();
        proxyParams.put("id", id);
        proxyParams.put("cid", clientId);
        proxyParams.put("pname", proxyName);
        proxyParams.put("ptype", proxyType.toUpperCase());
        proxyParams.put("lip", body.getStr("local_ip") != null ? body.getStr("local_ip") : "127.0.0.1");
        proxyParams.put("lp", localPort);
        proxyParams.put("rp", body.getInt("remote_port") != null ? body.getInt("remote_port") : 0);
        proxyParams.put("sk", body.getStr("secret_key") != null ? body.getStr("secret_key") : "");
        proxyParams.put("cd", body.get("custom_domains") != null ? JSONUtil.toJsonStr(body.get("custom_domains")) : "[]");
        proxyParams.put("enabled", body.getBool("enabled", true));
        proxyParams.put("remark", body.getStr("remark") != null ? body.getStr("remark") : "");

        SqlUtil.exec(
                "INSERT INTO frp_proxy (id, client_id, proxy_name, proxy_type, local_ip, local_port, "
                        + "remote_port, secret_key, custom_domains, enabled, remark, create_date, update_date) "
                        + "VALUES ({?|id?}, {?|cid?}, {?|pname?}, {?|ptype?}, {?|lip?}, {?|lp?}, "
                        + "{?|rp?}, {?|sk?}, {?|cd?}, {?|enabled?}, {?|remark?}, NOW(), NOW())",
                proxyParams);

        return Map.of("success", true, "id", id);
    }

    @PostMapping("/proxy/update")
    public Map<String, Object> proxyUpdate(@RequestBody JSONObject body) throws Exception {
        String id = body.getStr("id");
        if (id == null) return Map.of("success", false, "error", "缺少 id");

        Map<String, Object> proxyParams = new LinkedHashMap<>();
        proxyParams.put("id", id);
        proxyParams.put("pname", body.getStr("proxy_name"));
        proxyParams.put("ptype", body.getStr("proxy_type"));
        proxyParams.put("lip", body.getStr("local_ip") != null ? body.getStr("local_ip") : "127.0.0.1");
        proxyParams.put("lp", body.getInt("local_port"));
        proxyParams.put("rp", body.getInt("remote_port") != null ? body.getInt("remote_port") : 0);
        proxyParams.put("sk", body.getStr("secret_key") != null ? body.getStr("secret_key") : "");
        proxyParams.put("cd", body.get("custom_domains") != null ? JSONUtil.toJsonStr(body.get("custom_domains")) : "[]");
        proxyParams.put("enabled", body.getBool("enabled", true));
        proxyParams.put("remark", body.getStr("remark") != null ? body.getStr("remark") : "");

        SqlUtil.exec(
                "UPDATE frp_proxy SET proxy_name = {?|pname?}, proxy_type = {?|ptype?}, local_ip = {?|lip?}, "
                        + "local_port = {?|lp?}, remote_port = {?|rp?}, secret_key = {?|sk?}, "
                        + "custom_domains = {?|cd?}, enabled = {?|enabled?}, remark = {?|remark?}, update_date = NOW() "
                        + "WHERE id = {?|id?}",
                proxyParams);

        return Map.of("success", true);
    }

    @PostMapping("/proxy/delete")
    public Map<String, Object> proxyDelete(@RequestBody JSONObject body) throws Exception {
        String id = body.getStr("id");
        if (id == null) return Map.of("success", false, "error", "缺少 id");

        SqlUtil.exec("UPDATE frp_proxy SET del_flag = 1, update_date = NOW() WHERE id = {?|id?}", Map.of("id", id));
        return Map.of("success", true);
    }

    @PostMapping("/proxy/toggle")
    public Map<String, Object> proxyToggle(@RequestBody JSONObject body) throws Exception {
        String id = body.getStr("id");
        if (id == null) return Map.of("success", false, "error", "缺少 id");

        SqlUtil.exec("UPDATE frp_proxy SET enabled = NOT enabled, update_date = NOW() WHERE id = {?|id?}", Map.of("id", id));
        return Map.of("success", true);
    }

    // ======================== 配置生成 ========================

    @PostMapping("/client/generate-config")
    public Map<String, Object> generateConfig(@RequestBody JSONObject body) throws Exception {
        String clientId = body.getStr("client_id");
        if (clientId == null) return Map.of("success", false, "error", "缺少 client_id");

        // 查询客户端配置
        Map<String, Object> client = SqlUtil.row(
                "SELECT * FROM frp_client WHERE id = {?|cid?} AND del_flag = 0",
                Map.of("cid", clientId));
        if (client == null) return Map.of("success", false, "error", "客户端不存在");

        // 查询代理规则
        List<Map<String, Object>> proxies = SqlUtil.query(
                "SELECT proxy_name, proxy_type, local_ip, local_port, remote_port, secret_key, custom_domains "
                        + "FROM frp_proxy WHERE client_id = {?|cid?} AND del_flag = 0 AND enabled = true ORDER BY create_date",
                Map.of("cid", clientId), 100);

        // 构建 JSON 配置
        JSONObject config = new JSONObject();
        config.set("clientID", client.get("client_id") != null ? client.get("client_id").toString() : "");
        config.set("serverAddr", client.get("server_addr"));
        config.set("serverPort", client.get("server_port"));
        config.set("loginFailExit", client.get("login_fail_exit"));

        // auth
        JSONObject auth = new JSONObject();
        auth.set("method", "token");
        auth.set("token", client.get("auth_token") != null ? client.get("auth_token").toString() : "");
        config.set("auth", auth);

        // webServer
        int webPort = client.get("web_server_port") != null ? ((Number) client.get("web_server_port")).intValue() : 0;
        if (webPort > 0) {
            JSONObject webServer = new JSONObject();
            webServer.set("addr", client.get("web_server_addr") != null ? client.get("web_server_addr").toString() : "127.0.0.1");
            webServer.set("port", webPort);
            webServer.set("user", client.get("web_server_user") != null ? client.get("web_server_user").toString() : "");
            webServer.set("password", client.get("web_server_pass") != null ? client.get("web_server_pass").toString() : "");
            config.set("webServer", webServer);
        }

        // proxies
        JSONArray proxiesArr = new JSONArray();
        if (proxies != null) {
            for (Map<String, Object> p : proxies) {
                JSONObject proxy = new JSONObject();
                proxy.set("name", p.get("proxy_name"));
                String type = p.get("proxy_type").toString().toLowerCase();
                proxy.set("type", type);
                proxy.set("localIP", p.get("local_ip") != null ? p.get("local_ip").toString() : "127.0.0.1");
                proxy.set("localPort", p.get("local_port"));

                if (!"stcp".equals(type) && !"xtcp".equals(type)) {
                    proxy.set("remotePort", p.get("remote_port"));
                }

                Object sk = p.get("secret_key");
                if (sk != null && !sk.toString().isEmpty()) {
                    proxy.set("secretKey", sk.toString());
                }

                Object cd = p.get("custom_domains");
                if (cd != null && !cd.toString().isEmpty() && !"[]".equals(cd.toString())) {
                    try {
                        proxy.set("customDomains", JSONUtil.parseArray(cd.toString()));
                    } catch (Exception ignored) {}
                }

                proxiesArr.put(proxy);
            }
        }
        config.set("proxies", proxiesArr);

        // 写入配置文件
        String configPath = client.get("config_path") != null ? client.get("config_path").toString() : "";
        String frpBasePath = systemConfig.getFrpBasePath();
        String fullPath = null;
        String message = "";

        if (!configPath.isEmpty() && !frpBasePath.isEmpty()) {
            fullPath = frpBasePath + File.separator + configPath;
            try {
                Path parent = Path.of(fullPath).getParent();
                if (parent != null) Files.createDirectories(parent);
                Files.writeString(Path.of(fullPath), config.toStringPretty(), StandardCharsets.UTF_8);
                message = "配置已写入: " + fullPath;
            } catch (Exception e) {
                message = "写入失败: " + e.getMessage();
            }
        } else {
            message = "未配置路径，仅返回配置内容";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("config", config.toStringPretty());
        result.put("path", fullPath);
        result.put("message", message);
        return result;
    }

    // ======================== 重载 ========================

    @PostMapping("/client/reload")
    public Map<String, Object> clientReload(@RequestBody JSONObject body) throws Exception {
        String clientId = body.getStr("client_id");
        if (clientId == null) return Map.of("success", false, "error", "缺少 client_id");

        Map<String, Object> client = SqlUtil.row(
                "SELECT web_server_addr, web_server_port, web_server_user, web_server_pass "
                        + "FROM frp_client WHERE id = {?|cid?} AND del_flag = 0",
                Map.of("cid", clientId));
        if (client == null) return Map.of("success", false, "error", "客户端不存在");

        int webPort = client.get("web_server_port") != null ? ((Number) client.get("web_server_port")).intValue() : 0;
        if (webPort <= 0) return Map.of("success", false, "error", "未配置 WebServer 端口");

        String addr = client.get("web_server_addr") != null ? client.get("web_server_addr").toString() : "127.0.0.1";
        String user = client.get("web_server_user") != null ? client.get("web_server_user").toString() : "";
        String pass = client.get("web_server_pass") != null ? client.get("web_server_pass").toString() : "";

        String result = reloadFrp(addr, webPort, user, pass);
        return Map.of("success", result.contains("成功") || result.contains("ok"), "message", result);
    }

    // ======================== 状态查询 ========================

    @GetMapping("/client/status")
    public Map<String, Object> clientStatus(@RequestParam String clientId) throws Exception {
        Map<String, Object> client = SqlUtil.row(
                "SELECT web_server_addr, web_server_port, web_server_user, web_server_pass "
                        + "FROM frp_client WHERE id = {?|cid?} AND del_flag = 0",
                Map.of("cid", clientId));
        if (client == null) return Map.of("success", false, "error", "客户端不存在");

        int webPort = client.get("web_server_port") != null ? ((Number) client.get("web_server_port")).intValue() : 0;
        if (webPort <= 0) return Map.of("success", false, "error", "未配置 WebServer 端口");

        String addr = client.get("web_server_addr") != null ? client.get("web_server_addr").toString() : "127.0.0.1";
        String user = client.get("web_server_user") != null ? client.get("web_server_user").toString() : "";
        String pass = client.get("web_server_pass") != null ? client.get("web_server_pass").toString() : "";

        try {
            String url = "http://" + addr + ":" + webPort + "/api/status";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .header("Authorization", getBasicAuth(user, pass))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            // 更新心跳
            SqlUtil.exec("UPDATE frp_client SET connect_status = 1, last_heartbeat = NOW() WHERE id = {?|cid?}",
                    Map.of("cid", clientId));

            return Map.of("success", true, "status", 1, "data", response.body());
        } catch (Exception e) {
            SqlUtil.exec("UPDATE frp_client SET connect_status = 2 WHERE id = {?|cid?}",
                    Map.of("cid", clientId));
            return Map.of("success", false, "status", 2, "error", e.getMessage());
        }
    }

    // ======================== 内部工具 ========================

    private String reloadFrp(String addr, int port, String user, String pass) {
        try {
            String url = "http://" + addr + ":" + port + "/api/reload";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", getBasicAuth(user, pass))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 ? "重载成功" : "重载失败: HTTP " + response.statusCode();
        } catch (Exception e) {
            return "重载失败: " + e.getMessage();
        }
    }

    private String getBasicAuth(String user, String pass) {
        String auth = user + ":" + pass;
        return "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    }
}
