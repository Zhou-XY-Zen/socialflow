package com.socialflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * SocialFlow 应用程序启动类 —— 整个项目的入口，一切从这里开始。
 *
 * 这个类的作用是什么？
 * 这是一个 Spring Boot 应用程序的主类。当你运行 {@code main()} 方法时，
 * Spring Boot 会自动完成以下工作：
 *     - 创建 Spring 应用上下文（IoC 容器）
 *     - 扫描 {@code com.socialflow} 包及其子包下的所有组件
 *       （Controller、Service、Repository 等），并注册为 Bean
 *     - 读取配置文件（application.yml / application.properties）
 *     - 启动内嵌的 Tomcat Web 服务器
 *     - 初始化数据库连接池、Redis 连接等基础设施
 *
 * 注解说明：
 *     - {@code @SpringBootApplication(scanBasePackages = "com.socialflow")}
 *       —— 这是 Spring Boot 的核心注解，它是以下三个注解的组合：
 *           - @SpringBootConfiguration —— 标记这是一个配置类
 *           - @EnableAutoConfiguration —— 开启自动配置，Spring Boot 会根据引入的依赖
 *             自动配置相应的组件（如引入了 MySQL 驱动就自动配置数据源）
 *           - @ComponentScan —— 组件扫描，这里指定扫描 "com.socialflow" 包，
 *             这样即使项目分成了多个 Maven 模块（web、service、dao），
 *             只要它们的包名都在 com.socialflow 下，就都能被扫描到
 *     - {@code @EnableAsync} —— 开启异步方法支持。加了 @Async 注解的方法会在
 *       单独的线程中执行，不会阻塞调用者。本项目中，文档解析、评测任务执行等
 *       耗时操作使用了异步执行
 *     - {@code @EnableScheduling} —— 开启定时任务支持。加了 @Scheduled 注解的方法
 *       会按照设定的时间规则自动执行。可用于定时清理临时文件、定时统计数据等
 *     - {@code @EnableTransactionManagement} —— 开启声明式事务管理。
 *       在 Service 方法上添加 @Transactional 注解即可实现数据库事务控制，
 *       确保数据一致性（要么全部成功，要么全部回滚）
 *
 * 启动成功后，控制台会打印 SocialFlow 的 ASCII Art Logo，
 * 并显示 API 文档地址：{@code http://localhost:8080/doc.html}
 */
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
@SpringBootApplication(scanBasePackages = "com.socialflow")
public class SocialFlowApplication {

    /**
     * 应用程序主入口方法。
     *
     * 这是 Java 程序的标准入口。{@code SpringApplication.run()} 方法会启动整个
     * Spring Boot 应用，包括：
     *     - 加载所有配置和 Bean 定义
     *     - 创建并刷新 Spring 应用上下文
     *     - 启动内嵌的 Web 服务器（默认 Tomcat，监听 8080 端口）
     *     - 执行所有 CommandLineRunner 和 ApplicationRunner
     *
     * 启动完成后，打印项目 Logo 和 API 文档访问地址，提示开发者服务已就绪。
     *
     * @param args 命令行参数，可以通过命令行传入配置覆盖项
     *             （如 {@code --server.port=9090} 修改端口号）
     */
    public static void main(String[] args) {
        SpringApplication.run(SocialFlowApplication.class, args);
    }
}
