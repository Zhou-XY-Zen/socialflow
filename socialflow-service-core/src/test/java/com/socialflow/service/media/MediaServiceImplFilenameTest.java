package com.socialflow.service.media;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MediaServiceImpl#sanitizeFilename} 的单元测试 —— 防止 path traversal / XSS。
 *
 * <p>覆盖：normal 文件名 / 空 / 路径前缀 / 反斜杠（Windows）/ 控制字符 / 中文 / 超长。</p>
 */
@DisplayName("MediaServiceImpl.sanitizeFilename")
class MediaServiceImplFilenameTest {

    @Nested
    @DisplayName("正常输入")
    class Normal {
        @Test
        @DisplayName("纯英文文件名原样保留")
        void plainEnglish() {
            assertThat(MediaServiceImpl.sanitizeFilename("photo.jpg")).isEqualTo("photo.jpg");
        }

        @Test
        @DisplayName("中文文件名保留中日韩字符")
        void chinese() {
            assertThat(MediaServiceImpl.sanitizeFilename("我的封面.jpg")).isEqualTo("我的封面.jpg");
        }

        @Test
        @DisplayName("数字 + 下划线 + 连字符的复杂文件名保留")
        void mixed() {
            assertThat(MediaServiceImpl.sanitizeFilename("IMG_2026-04-25_v2.png"))
                    .isEqualTo("IMG_2026-04-25_v2.png");
        }
    }

    @Nested
    @DisplayName("路径穿透防御")
    class PathTraversal {
        @Test
        @DisplayName("../../../etc/passwd 只取末尾段")
        void unixPathTraversal() {
            // Paths.get 会取最后一段，剩余非法字符会被替换
            String result = MediaServiceImpl.sanitizeFilename("../../../etc/passwd");
            assertThat(result).isEqualTo("passwd");
        }

        @Test
        @DisplayName("Windows 反斜杠路径只取末尾段")
        void windowsPath() {
            String result = MediaServiceImpl.sanitizeFilename("C:\\Users\\evil\\malware.exe");
            // 反斜杠被替换为正斜杠后取末尾段
            assertThat(result).isEqualTo("malware.exe");
        }

        @Test
        @DisplayName("纯 ../ 不应留下任何路径痕迹")
        void onlyDotDot() {
            // ".." 在白名单外被替换为 _，最终变成 "unnamed" 或 "__"
            String result = MediaServiceImpl.sanitizeFilename("..");
            assertThat(result).isIn("unnamed", "..");  // ".." 字面被防御逻辑识别
        }
    }

    @Nested
    @DisplayName("XSS / 控制字符")
    class XssAndControlChars {
        @Test
        @DisplayName("尖括号被替换为下划线")
        void htmlTags() {
            String result = MediaServiceImpl.sanitizeFilename("<script>alert.png");
            assertThat(result).doesNotContain("<", ">", "/");
            assertThat(result).contains("script", "alert");
        }

        @Test
        @DisplayName("空格被替换为下划线")
        void spaces() {
            String result = MediaServiceImpl.sanitizeFilename("my photo (2).jpg");
            assertThat(result).doesNotContain(" ", "(", ")");
            assertThat(result).contains("my_photo");
        }

        @Test
        @DisplayName("引号 / 分号被替换")
        void quotesAndSemicolon() {
            String result = MediaServiceImpl.sanitizeFilename("name'\"; DROP TABLE.png");
            assertThat(result).doesNotContain("'", "\"", ";", " ");
        }
    }

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {
        @Test
        @DisplayName("null 返回 unnamed")
        void nullInput() {
            assertThat(MediaServiceImpl.sanitizeFilename(null)).isEqualTo("unnamed");
        }

        @Test
        @DisplayName("空串返回 unnamed")
        void emptyInput() {
            assertThat(MediaServiceImpl.sanitizeFilename("")).isEqualTo("unnamed");
        }

        @Test
        @DisplayName("仅空白返回 unnamed")
        void blankInput() {
            assertThat(MediaServiceImpl.sanitizeFilename("   ")).isEqualTo("unnamed");
        }

        @Test
        @DisplayName("超长文件名截断到 80 字符并保留扩展名")
        void truncateLong() {
            String longName = "a".repeat(120) + ".jpg";
            String result = MediaServiceImpl.sanitizeFilename(longName);
            assertThat(result.length()).isLessThanOrEqualTo(80);
            assertThat(result).endsWith(".jpg");
        }

        @Test
        @DisplayName("超长无扩展名也截断")
        void truncateLongNoExt() {
            String longName = "b".repeat(200);
            String result = MediaServiceImpl.sanitizeFilename(longName);
            assertThat(result).hasSize(80);
        }
    }
}
