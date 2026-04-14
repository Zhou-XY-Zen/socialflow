package com.socialflow.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 通用分页结果包装类
 *
 * 【作用】当查询数据量很大时（比如查所有文案列表），不能一次全部返回给前端，
 *   需要"分页"——每次只返回一页数据。这个类就是用来包装分页查询结果的。
 *
 * 【返回格式示例】
 * 
 *   {
 *     "records": [ ... ],  // 当前页的数据列表
 *     "total": 100,        // 满足条件的数据总条数
 *     "pageNum": 1,        // 当前是第几页（从1开始）
 *     "pageSize": 10       // 每页显示多少条
 *   }
 * 
 * 【使用场景】通常配合 R 类一起使用：
 *   return R.ok(PageResult.of(contentList, totalCount, pageNum, pageSize));
 *
 * 【泛型参数 T】records 列表中每个元素的类型，
 *   如 PageResult&lt;ContentVO&gt; 表示每条记录是一个 ContentVO
 *
 * @param <T> 分页数据中每条记录的类型
 */
@Data             // Lombok注解：自动生成 getter/setter/toString/equals/hashCode 方法
@NoArgsConstructor  // Lombok注解：自动生成无参构造方法（某些序列化框架需要）
@AllArgsConstructor // Lombok注解：自动生成全参构造方法
public class PageResult<T> implements Serializable {

    /** 序列化版本号，用于Java对象序列化/反序列化时的版本兼容性校验 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前页的数据列表，比如第1页的10条文案记录 */
    private List<T> records;

    /** 满足查询条件的数据总条数（不是当前页的条数），前端根据它计算总页数 */
    private long total;

    /** 当前页码，从1开始，前端翻页时传给后端 */
    private long pageNum;

    /** 每页显示多少条数据，前端可以让用户选择（比如10、20、50条/页） */
    private long pageSize;

    /**
     * 构建一个空的分页结果（没有查到任何数据时使用）
     * records 为空列表，total 为 0
     *
     * @param pageNum  当前页码
     * @param pageSize 每页条数
     * @param <T>      数据类型
     * @return 空的分页结果
     */
    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        return new PageResult<>(Collections.emptyList(), 0L, pageNum, pageSize);
    }

    /**
     * 构建一个有数据的分页结果
     *
     * @param records  当前页的数据列表
     * @param total    满足条件的数据总条数
     * @param pageNum  当前页码
     * @param pageSize 每页条数
     * @param <T>      数据类型
     * @return 分页结果
     */
    public static <T> PageResult<T> of(List<T> records, long total, long pageNum, long pageSize) {
        return new PageResult<>(records, total, pageNum, pageSize);
    }
}
