package com.example.localPicmaService.Class;

import cn.hutool.json.JSONObject;

import java.util.List;

/**
 * 自定义的返回消息类
 */
public class ZMessage {


    /**
     * Boolean 是否成功
     */
    public Boolean success;
    /**
     * String 返回的消息
     */
    public String msg;
    /**
     * HTTP传输code
     */
    public int code;
    /**
     * 返回的数据
     */
    public JSONObject data;

    public List<?> getItems() {
        return items;
    }

    public void setItems(List<?> items) {
        this.items = items;
    }

    public List<?> items;

    public ZMessage() {
    }

    public ZMessage(Boolean success, String msg, int code) {
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public JSONObject getData() {
        return data;
    }

    public void setData(JSONObject data) {
        this.data = data;
    }

    /**
     * 设置类型参数
     *
     * @param msg
     */
    public void setSucc(String msg) {
        this.setSuccess(true);
        this.setCode(200);
        this.setMsg(msg);
    }

    public void setSucc(String msg, JSONObject data) {
        this.setSuccess(true);
        this.setCode(200);
        this.setData(new JSONObject(data));
        this.setMsg(msg);
    }

    public void setSucc(String msg, JSONObject data, List<?> items) {
        this.setSuccess(true);
        this.setCode(200);
        this.setData(new JSONObject(data));
        this.setMsg(msg);
        this.setItems(items);
    }

    public void setfail(String msg) {
        this.setSuccess(false);
        this.setCode(400);
        this.setMsg(msg);
    }
}
