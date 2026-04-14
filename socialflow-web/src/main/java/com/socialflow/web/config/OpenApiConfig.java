package com.socialflow.web.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI（Swagger）文档配置类 —— 配置在线 API 文档的基本信息和认证方式。
 *
 * 什么是 Swagger / OpenAPI？
 * Swagger 是一个 API 文档自动生成工具，能根据代码中的注解自动生成交互式 API 文档。
 * OpenAPI 是 Swagger 遵循的标准规范。本项目使用 Knife4j（一个增强版的 Swagger UI），
 * 启动后访问 {@code http://localhost:8080/doc.html} 即可查看所有接口的文档，
 * 还可以直接在页面上测试接口，非常方便开发和调试。
 *
 * 为什么需要这个配置？
 * 虽然 Swagger 可以自动扫描控制器生成文档，但我们需要手动配置一些全局信息：
 *     - API 文档的标题、描述、版本号 —— 让文档更专业
 *     - 全局的认证方式 —— 让 Swagger UI 支持在请求中携带 Token，
 *       方便测试需要登录的接口
 */
/*
 * @Configuration —— Spring 配置类，启动时自动加载
 */
@Configuration
public class OpenApiConfig {

    /**
     * 创建 OpenAPI 文档配置 Bean。
     *
     * 配置内容说明：
     *     - Info 部分：设置 API 文档的元信息
     *         - title —— 文档标题："SocialFlow API"
     *         - description —— 项目描述："AI 驱动的社交媒体内容运营平台"
     *         - version —— API 版本号："1.0.0"
     *         - contact —— 联系人信息
     *     - Security 部分：配置全局的认证方案
     *         - SecurityRequirement —— 声明所有接口默认都需要 "Authorization" 认证
     *         - SecurityScheme —— 定义认证方案的细节：
     *             - type = APIKEY —— 使用 API Key 方式认证（即在 Header 中传递 Token）
     *             - in = HEADER —— Token 放在 HTTP 请求头中
     *             - name = "Authorization" —— Header 的名称为 "Authorization"
     *         这样配置后，Swagger UI 页面顶部会出现一个 "Authorize" 按钮，
     *         用户输入 Token 后，后续的所有测试请求都会自动带上这个 Token。
     *
     * @return 配置好的 OpenAPI 实例
     */
    @Bean
    public OpenAPI socialflowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SocialFlow API")
                        .description("AI-powered social media content operation platform")
                        .version("1.0.0")
                        .contact(new Contact().name("SocialFlow")))
                .addSecurityItem(new SecurityRequirement().addList("Authorization"))
                .components(new Components().addSecuritySchemes("Authorization",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("Authorization")));
    }
}
