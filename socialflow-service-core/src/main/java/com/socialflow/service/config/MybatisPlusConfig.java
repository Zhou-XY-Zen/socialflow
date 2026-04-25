package com.socialflow.service.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 全局配置类 —— 配置分页插件、乐观锁插件和 Mapper 扫描路径。
 *
 * 本类是 MyBatis-Plus 框架的核心配置，主要完成两件事：
 *     - 通过 {@code @MapperScan} 注解告诉 Spring 去哪个包下扫描 Mapper 接口
 *     - 注册 MyBatis-Plus 的拦截器插件（分页和乐观锁）
 *
 * 关于 {@code @MapperScan("com.socialflow.dao.mapper")} 注解：
 *     - Spring 启动时会扫描指定包路径下的所有 Mapper 接口
 *     - 自动为每个 Mapper 接口创建代理实现类并注册为 Spring Bean
 *     - 这样在 Service 层就可以直接 {@code @Autowired} 注入 Mapper 使用
 *     - 如果不配置此注解，Mapper 接口就不会被识别，注入时会报错
 *
 * 本类不对应任何 Controller，属于底层基础设施配置，影响所有 Mapper 的行为。
 */
@Configuration  // Spring 注解：标记这是一个配置类，等同于 XML 配置文件，Spring 启动时会自动加载
@MapperScan("com.socialflow.dao.mapper")  // MyBatis 注解：指定 Mapper 接口所在的包路径，Spring 会自动扫描并注册
public class MybatisPlusConfig {

    /**
     * 注册 MyBatis-Plus 拦截器（插件机制）。
     *
     * MyBatis-Plus 通过拦截器机制扩展 MyBatis 的功能。拦截器会在 SQL 执行前后进行拦截处理，
     * 实现分页、乐观锁等高级功能，而无需修改业务代码。
     *
     * 本方法注册了两个内置插件：
     *     - 分页插件（PaginationInnerInterceptor）—— 自动改写 SQL 实现物理分页。
     *       没有此插件时，{@code Page} 对象的分页不会生效，会查出全部数据。
     *       指定 {@code DbType.MYSQL} 是为了让插件生成 MySQL 语法的 LIMIT 子句。
     *     - 乐观锁插件（OptimisticLockerInnerInterceptor）—— 在 UPDATE 时自动检查版本号。
     *       实体类中标注了 {@code @Version} 的字段会被用作版本号，
     *       更新时会在 WHERE 条件中加上 {@code AND version = 旧版本号}，
     *       并将版本号 +1。如果版本号不匹配则更新失败，防止并发修改冲突。
     *
     * 注意：插件的添加顺序有讲究，分页插件建议放在最前面。
     *
     * @return 配置好的 MyBatis-Plus 拦截器实例
     */
    @Bean  // Spring 注解：将方法的返回值注册为 Spring Bean，其他地方可以自动注入使用
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        // 创建 MyBatis-Plus 拦截器的外壳（可以包含多个内部插件）
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 添加分页插件 —— 指定数据库类型为 MySQL，插件会生成 MySQL 语法的分页 SQL
        // 例如：原始 SQL "SELECT * FROM content" 会被改写为 "SELECT * FROM content LIMIT 0, 20"
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));

        // 添加乐观锁插件 —— 防止并发更新时数据覆盖
        // 例如：两个请求同时修改同一条记录，只有先到的那个会成功，后到的因为版本号不匹配会更新失败
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        return interceptor;
    }
}
