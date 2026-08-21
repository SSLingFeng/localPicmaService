package com.example.localPicmaService.config;

import org.springframework.boot.web.error.ErrorPage;
import org.springframework.boot.web.error.ErrorPageRegistrar;
import org.springframework.boot.web.error.ErrorPageRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

/**
 * 错误页配置 —— 将 4xx/5xx 错误统一引导到自定义错误页
 */
@Configuration
public class ErrorPageConfig {

    @Bean
    public ErrorPageRegistrar errorPageRegistrar() {
        return new ErrorPageRegistrar() {
            @Override
            public void registerErrorPages(ErrorPageRegistry registry) {
                registry.addErrorPages(
                    new ErrorPage(HttpStatus.UNAUTHORIZED,       "/public/res/error/error.html?code=401"),
                    new ErrorPage(HttpStatus.FORBIDDEN,          "/public/res/error/error.html?code=403"),
                    new ErrorPage(HttpStatus.NOT_FOUND,          "/public/res/error/error.html?code=404"),
                    new ErrorPage(HttpStatus.METHOD_NOT_ALLOWED, "/public/res/error/error.html?code=405"),
                    new ErrorPage(HttpStatus.INTERNAL_SERVER_ERROR, "/public/res/error/error.html?code=500"),
                    new ErrorPage(HttpStatus.BAD_GATEWAY,        "/public/res/error/error.html?code=502"),
                    new ErrorPage(HttpStatus.SERVICE_UNAVAILABLE,"/public/res/error/error.html?code=503")
                );
            }
        };
    }
}
