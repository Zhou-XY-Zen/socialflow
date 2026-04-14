package com.socialflow.common.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 加密/解密工具类
 *
 * 【作用】对系统中的敏感字段进行加密存储，如：
 *   - 第三方平台的 API Key（如OpenAI的密钥）
 *   - OAuth授权令牌（如微信公众号的access_token）
 *   - 其他不能明文存储在数据库中的敏感信息
 *
 * 【什么是 AES-256-GCM】
 *   AES（Advanced Encryption Standard）是目前最常用的对称加密算法。
 *   - "256"指密钥长度为256位（32字节），安全性非常高
 *   - "GCM"（Galois/Counter Mode）是一种加密模式，它不仅加密数据，
 *     还能验证数据是否被篡改（认证加密），比普通的CBC模式更安全
 *   - "NoPadding"表示GCM模式不需要额外的填充
 *
 * 【加密后的数据格式】（Base64编码后的字符串）
 *   Base64( IV(12字节) + 密文 + 认证标签(16字节) )
 *   - IV（Initialization Vector，初始化向量）：每次加密随机生成，保证相同明文每次加密结果不同
 *   - 密文：加密后的数据
 *   - 认证标签（Tag）：用于解密时验证数据完整性，防止密文被篡改
 *
 * 【使用示例】
 *   // 加密：hexKey 是64个十六进制字符（=32字节=256位的AES密钥）
 *   String encrypted = AesGcmUtil.encrypt("sk-abc123...", hexKey);
 *   // 解密：
 *   String decrypted = AesGcmUtil.decrypt(encrypted, hexKey);
 *
 * 【安全注意事项】
 *   - hexKey 绝不能硬编码在代码中，应该从环境变量或配置中心获取
 *   - 每次加密都会生成新的随机IV，所以同一个明文加密两次结果不同（这是正常的）
 */
public final class AesGcmUtil {

    /** 加密算法标识：AES算法 + GCM模式 + 无填充 */
    private static final String ALGO = "AES/GCM/NoPadding";

    /** IV（初始化向量）的长度：12字节，这是GCM模式推荐的IV长度 */
    private static final int IV_BYTES = 12;

    /** GCM认证标签的长度：128位（16字节），用于验证密文完整性 */
    private static final int TAG_BITS = 128;

    /** 私有构造方法，禁止实例化此工具类 */
    private AesGcmUtil() {}

    /**
     * 加密明文字符串
     *
     * 【加密步骤详解】
     *   1. 将十六进制格式的密钥转换为字节数组
     *   2. 随机生成12字节的IV（每次加密都不同，保证安全性）
     *   3. 用密钥和IV初始化AES-GCM加密器
     *   4. 执行加密，得到 密文+认证标签
     *   5. 将 IV + 密文+标签 拼接在一起
     *   6. 对拼接结果做Base64编码，转成可存储的字符串
     *
     * @param plaintext 要加密的明文字符串（如API Key）
     * @param hexKey    AES-256密钥的十六进制字符串（必须是64个十六进制字符 = 32字节）
     * @return Base64编码的密文字符串，可安全存储到数据库中
     * @throws IllegalStateException 加密过程出错时抛出
     */
    public static String encrypt(String plaintext, String hexKey) {
        try {
            // 第1步：将十六进制格式的密钥字符串转换为字节数组（32字节）
            byte[] key = hexToBytes(hexKey);

            // 第2步：生成12字节的随机IV（初始化向量）
            // SecureRandom 是密码学安全的随机数生成器，比普通 Random 更安全
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);

            // 第3步：创建并初始化AES-GCM加密器
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE,          // 设置为加密模式
                    new SecretKeySpec(key, "AES"),      // 用字节数组创建AES密钥对象
                    new GCMParameterSpec(TAG_BITS, iv)); // 设置GCM参数（标签长度和IV）

            // 第4步：执行加密——将明文转为UTF-8字节后加密，得到 密文+认证标签
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 第5步：将 IV 和 密文+标签 拼接成一个字节数组
            // 格式：[IV(12字节)] [密文+Tag]
            // 解密时需要从中拆分出IV和密文
            byte[] combined = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);       // 前12字节放IV
            System.arraycopy(ct, 0, combined, iv.length, ct.length); // 后面放密文+Tag

            // 第6步：对拼接后的字节数组做Base64编码，转成可存储的字符串
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM encrypt failed", e);
        }
    }

    /**
     * 解密Base64编码的密文字符串
     *
     * 【解密步骤详解】
     *   1. 将十六进制格式的密钥转换为字节数组
     *   2. 对Base64密文解码，得到 IV + 密文+标签 的拼接字节数组
     *   3. 从拼接数组中拆分出IV（前12字节）和密文+标签（剩余部分）
     *   4. 用密钥和IV初始化AES-GCM解密器
     *   5. 执行解密（GCM会自动验证认证标签，如果数据被篡改会报错）
     *   6. 将解密后的字节数组转为UTF-8字符串
     *
     * @param base64 Base64编码的密文（由 encrypt 方法生成）
     * @param hexKey AES-256密钥的十六进制字符串（必须与加密时使用的密钥相同）
     * @return 解密后的明文字符串
     * @throws IllegalStateException 解密过程出错时抛出（密钥错误、数据被篡改等）
     */
    public static String decrypt(String base64, String hexKey) {
        try {
            // 第1步：将十六进制格式的密钥字符串转换为字节数组
            byte[] key = hexToBytes(hexKey);

            // 第2步：对Base64密文解码，得到原始字节数组（IV + 密文 + Tag）
            byte[] combined = Base64.getDecoder().decode(base64);

            // 第3步：从拼接数组中拆分出IV和密文
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_BYTES);                // 取前12字节作为IV
            byte[] ct = new byte[combined.length - IV_BYTES];
            System.arraycopy(combined, IV_BYTES, ct, 0, ct.length);        // 取剩余部分作为密文+Tag

            // 第4步：创建并初始化AES-GCM解密器
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE,           // 设置为解密模式
                    new SecretKeySpec(key, "AES"),       // 使用相同的密钥
                    new GCMParameterSpec(TAG_BITS, iv)); // 使用从密文中提取的IV

            // 第5步：执行解密并转为字符串
            // GCM模式会自动验证认证标签，如果密文被篡改会抛出 AEADBadTagException
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM decrypt failed", e);
        }
    }

    /**
     * 将十六进制字符串转换为字节数组
     *
     * 【为什么需要这个方法】
     *   密钥在配置文件中通常以十六进制字符串形式存储（如 "a1b2c3d4..."），
     *   但加密算法需要的是字节数组，所以需要做转换。
     *   每2个十六进制字符代表1个字节，所以64个十六进制字符 = 32字节 = 256位密钥。
     *
     * 【转换原理】
     *   十六进制 "a1" → 十进制 161 → 二进制 10100001 → 1个字节
     *   具体实现：高位字符左移4位 + 低位字符的值
     *   例如："a1" → (a=10, 左移4位=160) + (1=1) = 161
     *
     * @param hex 十六进制字符串，必须是64个字符（32字节的AES-256密钥）
     * @return 转换后的字节数组
     * @throws IllegalArgumentException 如果hex为null或长度不是64
     */
    private static byte[] hexToBytes(String hex) {
        // 校验密钥不能为null
        if (hex == null) throw new IllegalArgumentException("key hex is null");
        int len = hex.length();
        // 校验密钥长度必须是64个十六进制字符（= 32字节 = 256位）
        if (len != 64) {
            throw new IllegalArgumentException("AES-256 key must be 32 bytes (64 hex chars)");
        }
        // 每2个十六进制字符转为1个字节
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            // 高位字符转为数值后左移4位（乘以16），加上低位字符的数值
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }
}
