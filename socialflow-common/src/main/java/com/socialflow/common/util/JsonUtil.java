package com.socialflow.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * JSON序列化/反序列化工具类（基于Jackson库）
 *
 * 【作用】提供统一的JSON处理方法，在整个应用中共享同一个 ObjectMapper 实例。
 *   - 序列化（toJson）：把Java对象转成JSON字符串，用于接口响应、消息队列、缓存存储等
 *   - 反序列化（fromJson）：把JSON字符串转回Java对象，用于解析请求体、读取缓存等
 *
 * 【为什么要统一管理 ObjectMapper】
 *   1. ObjectMapper 是线程安全的，但创建成本较高，所以整个应用共用一个实例
 *   2. 统一配置序列化/反序列化行为，避免各处配置不一致导致的bug
 *   3. 集中处理异常，不需要每个地方都写 try-catch
 *
 * 【全局配置说明】
 *   - JavaTimeModule：支持 Java 8 的时间类型（LocalDateTime、LocalDate等）的序列化
 *   - WRITE_DATES_AS_TIMESTAMPS=false：日期输出为字符串格式（如"2024-01-15"），而不是时间戳数字
 *   - FAIL_ON_UNKNOWN_PROPERTIES=false：反序列化时忽略JSON中有但Java类中没有的字段，增强兼容性
 *
 * 【使用示例】
 *   // 对象转JSON字符串
 *   String json = JsonUtil.toJson(contentVO);
 *   // JSON字符串转对象
 *   ContentVO vo = JsonUtil.fromJson(jsonStr, ContentVO.class);
 *   // JSON字符串转泛型集合
 *   List&lt;ContentVO&gt; list = JsonUtil.fromJson(jsonStr, new TypeReference&lt;List&lt;ContentVO&gt;&gt;() {});
 */
public final class JsonUtil {

    /**
     * 全局共享的 ObjectMapper 实例（线程安全）
     *
     * 配置说明：
     * - registerModule(JavaTimeModule)：注册Java 8时间模块，使LocalDateTime等类型能正确序列化
     * - disable(WRITE_DATES_AS_TIMESTAMPS)：日期序列化为ISO格式字符串，而不是数字时间戳
     * - disable(FAIL_ON_UNKNOWN_PROPERTIES)：反序列化时遇到未知字段不报错（增强向前兼容性）
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())                          // 支持 LocalDateTime 等Java 8时间类型
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)       // 日期输出为字符串而非时间戳
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);   // 忽略JSON中多余的字段

    /** 私有构造方法，禁止实例化此工具类 */
    private JsonUtil() {}

    /**
     * 获取全局共享的 ObjectMapper 实例
     * 当需要进行更复杂的JSON操作时（如流式解析），可以直接使用这个 ObjectMapper
     *
     * @return 全局 ObjectMapper 实例
     */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * 将Java对象序列化为JSON字符串
     *
     * @param obj 要序列化的Java对象（可以是任何类型：POJO、List、Map等）
     * @return JSON格式的字符串
     * @throws IllegalStateException 序列化失败时抛出（如对象中有循环引用等）
     */
    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("json serialize failed", e);
        }
    }

    /**
     * 将JSON字符串反序列化为指定类型的Java对象
     * 适用于简单类型（非泛型），如 ContentVO.class、UserDTO.class
     *
     * @param json JSON格式的字符串
     * @param type 目标Java类型的Class对象
     * @param <T>  目标类型
     * @return 反序列化后的Java对象
     * @throws IllegalStateException 反序列化失败时抛出（如JSON格式错误、字段类型不匹配等）
     */
    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("json deserialize failed", e);
        }
    }

    /**
     * 将JSON字符串反序列化为复杂泛型类型的Java对象
     * 适用于带泛型的类型，如 List&lt;ContentVO&gt;、Map&lt;String, Object&gt; 等。
     * 因为Java的类型擦除，无法用 Class 表示泛型类型，所以需要用 TypeReference。
     *
     * 【使用示例】
     *   List&lt;ContentVO&gt; list = JsonUtil.fromJson(json, new TypeReference&lt;List&lt;ContentVO&gt;&gt;() {});
     *   Map&lt;String, Object&gt; map = JsonUtil.fromJson(json, new TypeReference&lt;Map&lt;String, Object&gt;&gt;() {});
     *
     * @param json JSON格式的字符串
     * @param type TypeReference 类型引用，用匿名内部类的方式保留泛型信息
     * @param <T>  目标类型
     * @return 反序列化后的Java对象
     * @throws IllegalStateException 反序列化失败时抛出
     */
    public static <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("json deserialize failed", e);
        }
    }
}
