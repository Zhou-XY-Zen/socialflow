package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 所有业务实体的公共基类 —— 不对应单独的数据库表，而是被其他实体继承
 *
 * 【作用】
 *   把每张业务表都会有的公共字段（主键、创建时间、更新时间、逻辑删除标识）
 *   抽取到一个父类中，避免在每个实体类里重复编写。
 *   所有业务实体类（如 SysUser、Content、KnowledgeBase 等）都继承自本类。
 *
 * 【为什么需要它】
 *   1. 减少重复代码：公共字段只写一次，所有子类自动拥有
 *   2. 统一规范：保证所有表的主键策略、时间填充策略、逻辑删除策略一致
 *   3. 配合 MyBatis-Plus 的自动填充功能（MetaObjectHandler），在 INSERT/UPDATE 时自动设置时间字段
 *
 * 【使用场景】
 *   在定义任何新的业务实体时，直接 extends BaseEntity 即可获得 id、createTime、updateTime、isDeleted 四个字段。
 *
 * 【注意】
 *   本类是 abstract（抽象类），不能直接实例化，只能被子类继承。
 *   实现了 Serializable 接口，以便在网络传输和缓存中进行序列化。
 */
@Data
public abstract class BaseEntity implements Serializable {

    /** 序列化版本号，用于 Java 序列化机制确保版本兼容性 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID
     *
     * 使用 MyBatis-Plus 的 ASSIGN_ID 策略，即雪花算法（Snowflake）自动生成全局唯一的 Long 型 ID。
     * 不依赖数据库自增，适合分布式环境。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 记录创建时间
     *
     * 在执行 INSERT 操作时，由 MyBatis-Plus 的 MetaObjectHandler 自动填充为当前时间。
     * 对应数据库字段：create_time
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 记录最后更新时间
     *
     * 在执行 INSERT 和 UPDATE 操作时，都会由 MyBatis-Plus 自动填充为当前时间。
     * 对应数据库字段：update_time
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标识
     *
     * 0 = 未删除（正常），1 = 已删除。
     * 使用 @TableLogic 注解后，调用 MyBatis-Plus 的删除方法不会真正删除数据库记录，
     * 而是将 is_deleted 字段更新为 1，查询时也会自动过滤已删除的记录。
     * 对应数据库字段：is_deleted
     */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
