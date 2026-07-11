package com.meetingos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final Path uploadRoot;

    public WebConfig(@Value("${meetingos.app.upload-dir:uploads}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        /*
         * 公开查询页只需要渲染后台上传的图片。任务输入、失败明细和固定导出文件也位于
         * uploadRoot，但必须经过带会议权限校验的控制器下载，不能落入匿名静态映射。
         */
        registry.addResourceHandler("/uploads/images/**")
                .addResourceLocations(uploadRoot.resolve("images").toUri().toString() + "/");
    }
}
