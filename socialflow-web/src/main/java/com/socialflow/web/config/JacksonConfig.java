package com.socialflow.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 全局配置 —— 解决雪花 ID 在 JavaScript 中精度丢失的问题。
 *
 * 只把 Long 包装类型（对象字段如 id）转为字符串，
 * 不影响 long 基本类型（如 total、pageNum、pageSize 等数值字段）。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> {
            // 只转 Long 包装类（实体类的 id 字段），不转 long 基本类型（total/pageNum 等）
            builder.serializerByType(Long.class, ToStringSerializer.instance);
        };
    }
}
