package com.example.localPicmaService.base;

public class test {
    public static void main(String[] args) {
        String sql = "select * from frp_config_list";
        Object s = DataSourceControl.runQuery(sql);
        System.out.println(s);
    }
}
