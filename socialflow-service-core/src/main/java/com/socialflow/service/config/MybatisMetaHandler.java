package com.socialflow.service.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充处理器 —— 自动维护公共字段的值。
 *
 * 在实际业务开发中，几乎每张数据库表都有一些公共字段，如：
 *     - {@code create_time} —— 记录创建时间
 *     - {@code update_time} —— 记录最后修改时间
 *     - {@code is_deleted} —— 逻辑删除标记（0=未删除，1=已删除）
 * 如果每次新增或修改数据时都手动设置这些字段，不仅繁琐，还容易遗漏。
 *
 * 自动填充机制的工作原理：
 *     - 在实体类（Entity）的字段上添加 {@code @TableField(fill = FieldFill.INSERT)} 等注解，
 *       告诉 MyBatis-Plus 哪些字段需要自动填充
 *     - 本类实现了 {@link MetaObjectHandler} 接口，重写了 insertFill 和 updateFill 方法
 *     - 当执行 INSERT 操作时，MyBatis-Plus 自动调用 insertFill 方法填充字段
 *     - 当执行 UPDATE 操作时，MyBatis-Plus 自动调用 updateFill 方法填充字段
 *
 * 本类不对应任何 Controller，属于底层基础设施配置，被所有使用 MyBatis-Plus 的
 * Mapper（如 ContentMapper、UserMapper 等）间接调用。
 *
 * @see com.baomidou.mybatisplus.core.handlers.MetaObjectHandler MyBatis-Plus 元数据处理器接口
 */
@Component  // Spring 注解：注册为 Spring Bean，MyBatis-Plus 会自动发现并使用
public class MybatisMetaHandler implements MetaObjectHandler {

    /**
     * 插入（INSERT）时的自动填充逻辑。
     *
     * 当执行数据库 INSERT 操作时，MyBatis-Plus 会自动调用此方法，
     * 为标记了 {@code fill = FieldFill.INSERT} 的字段设置默认值。
     *
     * 填充规则：
     *     - {@code createTime} —— 设置为当前时间（记录何时创建）
     *     - {@code updateTime} —— 也设置为当前时间（新记录的创建时间即为最后更新时间）
     *     - {@code isDeleted} —— 设置为 0（表示未删除，新记录默认是有效的）
     *
     * strictInsertFill 是"严格模式"填充 —— 只有当字段值为 null 时才会填充，
     * 如果代码中已经手动设置了值，则不会被覆盖。
     *
     * @param metaObject MyBatis-Plus 传入的元对象，包装了当前要插入的实体
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        // 获取当前时间，createTime 和 updateTime 使用同一个时间点，保证一致性
        LocalDateTime now = LocalDateTime.now();
        // 填充创建时间
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        // 填充更新时间（新记录的创建时间 = 更新时间）
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        // 填充逻辑删除标记为 0（未删除）
        strictInsertFill(metaObject, "isDeleted", Integer.class, 0);
    }

    /**
     * 更新（UPDATE）时的自动填充逻辑。
     *
     * 当执行数据库 UPDATE 操作时，MyBatis-Plus 会自动调用此方法，
     * 为标记了 {@code fill = FieldFill.UPDATE} 的字段设置新值。
     *
     * 填充规则：只更新 {@code updateTime} 为当前时间，
     * 这样每次修改记录时，最后更新时间会自动刷新，无需手动设置。
     *
     * @param metaObject MyBatis-Plus 传入的元对象，包装了当前要更新的实体
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        // 将 updateTime 设置为当前时间，记录最近一次修改的时刻
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
