package com.socialflow.service.ai.prompt;

import com.socialflow.dao.mapper.PromptTemplateMapper;
import com.socialflow.model.entity.PromptTemplate;
import com.socialflow.service.ai.llm.ChatMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * {@link PromptServiceImpl} 的单元测试 —— 覆盖模板渲染管道的关键分支：
 *
 * <ul>
 *   <li>renderInline：变量替换、null 安全、未知变量保留原占位符、条件块解析</li>
 *   <li>render：模板加载（按 ID / 平台默认 / 兜底）、Few-Shot 解析、RAG 上下文注入</li>
 * </ul>
 *
 * <p>不启动 Spring 容器；PromptTemplateMapper 通过 Mockito mock。</p>
 */
@ExtendWith(MockitoExtension.class)
class PromptServiceImplTest {

    @Mock
    private PromptTemplateMapper promptTemplateMapper;

    @Mock
    private PromptTemplateLookup templateLookup;

    @InjectMocks
    private PromptServiceImpl promptService;

    @Nested
    @DisplayName("renderInline 行内渲染")
    class RenderInline {

        @Test
        @DisplayName("空模板返回空串")
        void blankTemplate_returnsEmpty() {
            assertThat(promptService.renderInline(null, Map.of())).isEmpty();
            assertThat(promptService.renderInline("", Map.of())).isEmpty();
            assertThat(promptService.renderInline("   ", Map.of())).isEmpty();
        }

        @Test
        @DisplayName("基础变量替换：{{name}} → 实际值")
        void replacesPlaceholders() {
            String result = promptService.renderInline(
                    "Hello {{name}}, today is {{day}}",
                    Map.of("name", "Alice", "day", "Monday"));

            assertThat(result).isEqualTo("Hello Alice, today is Monday");
        }

        @Test
        @DisplayName("未在 variables 中的变量保留原占位符（便于前端高亮缺失）")
        void unknownPlaceholder_kept() {
            String result = promptService.renderInline(
                    "Hello {{name}}, today is {{day}}",
                    Map.of("name", "Alice"));

            assertThat(result).isEqualTo("Hello Alice, today is {{day}}");
        }

        @Test
        @DisplayName("variables 中 value=null 替换为空字符串")
        void nullValue_replacedWithEmpty() {
            Map<String, Object> vars = new HashMap<>();
            vars.put("name", null);

            String result = promptService.renderInline("Hello {{name}}!", vars);

            assertThat(result).isEqualTo("Hello !");
        }

        @Test
        @DisplayName("条件块 {{#var}}...{{/var}}：变量存在时保留块内容")
        void conditionalBlock_present() {
            String result = promptService.renderInline(
                    "Hi{{#tag}} #{{tag}}{{/tag}}!",
                    Map.of("tag", "ai"));

            assertThat(result).isEqualTo("Hi #ai!");
        }

        @Test
        @DisplayName("条件块：变量不存在时整段被移除")
        void conditionalBlock_absent_removed() {
            String result = promptService.renderInline(
                    "Hi{{#tag}} #{{tag}}{{/tag}}!",
                    Map.of("name", "Alice"));

            assertThat(result).isEqualTo("Hi!");
        }

        @Test
        @DisplayName("条件块：变量为空字符串时整段被移除")
        void conditionalBlock_emptyString_removed() {
            String result = promptService.renderInline(
                    "Hi{{#tag}} #{{tag}}{{/tag}}!",
                    Map.of("tag", ""));

            assertThat(result).isEqualTo("Hi!");
        }

        @Test
        @DisplayName("variables 为 null 时清理所有条件块")
        void nullVariables_cleansConditionalBlocks() {
            String result = promptService.renderInline(
                    "Hi{{#tag}} #{{tag}}{{/tag}}!",
                    null);

            assertThat(result).isEqualTo("Hi!");
        }
    }

    @Nested
    @DisplayName("render 完整模板渲染")
    class Render {

        @Test
        @DisplayName("templateId 命中数据库：使用模板的 systemPrompt + userPromptTemplate")
        void byTemplateId_usesDbTemplate() {
            PromptTemplate tpl = new PromptTemplate();
            tpl.setId(100L);
            tpl.setSystemPrompt("你是社媒文案专家");
            tpl.setUserPromptTemplate("写一篇关于 {{topic}} 的{{platform}}文案");
            when(templateLookup.findById(100L)).thenReturn(tpl);

            List<ChatMessage> messages = promptService.render(
                    100L, "XIAOHONGSHU",
                    Map.of("topic", "咖啡", "platform", "小红书"),
                    null);

            assertThat(messages).hasSize(2);
            assertThat(messages.get(0).getRole()).isEqualTo("system");
            assertThat(messages.get(0).getContent()).isEqualTo("你是社媒文案专家");
            assertThat(messages.get(1).getRole()).isEqualTo("user");
            assertThat(messages.get(1).getContent()).isEqualTo("写一篇关于 咖啡 的小红书文案");
        }

        @Test
        @DisplayName("templateId=null 时按 platform 默认模板加载")
        void byPlatform_defaultTemplate() {
            PromptTemplate tpl = new PromptTemplate();
            tpl.setId(200L);
            tpl.setPlatform("XIAOHONGSHU");
            tpl.setSystemPrompt("默认平台模板");
            tpl.setUserPromptTemplate("topic: {{topic}}");
            when(templateLookup.findDefaultByPlatform("XIAOHONGSHU")).thenReturn(tpl);

            List<ChatMessage> messages = promptService.render(
                    null, "XIAOHONGSHU", Map.of("topic", "茶"), null);

            assertThat(messages).hasSize(2);
            assertThat(messages.get(0).getContent()).isEqualTo("默认平台模板");
            assertThat(messages.get(1).getContent()).isEqualTo("topic: 茶");
        }

        @Test
        @DisplayName("templateId 和 platform 都没匹配时使用兜底提示词")
        void noTemplate_fallback() {
            // 没设置 stub 返回值 → mock 默认返回 null
            // 用 lenient 避免严格 stubbing 校验
            lenient().when(templateLookup.findById(any())).thenReturn(null);
            lenient().when(templateLookup.findDefaultByPlatform(any())).thenReturn(null);

            List<ChatMessage> messages = promptService.render(
                    null, null,
                    Map.of("topic", "咖啡", "platform", "小红书", "wordCount", 200),
                    null);

            assertThat(messages).hasSize(2);
            assertThat(messages.get(0).getRole()).isEqualTo("system");
            assertThat(messages.get(0).getContent()).contains("社媒文案写手");
            assertThat(messages.get(1).getContent()).contains("咖啡").contains("200");
        }

        @Test
        @DisplayName("RAG 上下文注入：用户消息末尾追加【参考资料】区块")
        void ragContext_appendedToUserMessage() {
            PromptTemplate tpl = new PromptTemplate();
            tpl.setId(300L);
            tpl.setSystemPrompt("sys");
            tpl.setUserPromptTemplate("写关于 {{topic}}");
            when(templateLookup.findById(300L)).thenReturn(tpl);

            List<ChatMessage> messages = promptService.render(
                    300L, "XIAOHONGSHU", Map.of("topic", "猫"), "参考文档：猫的习性...");

            String userMsg = messages.get(messages.size() - 1).getContent();
            assertThat(userMsg).startsWith("写关于 猫");
            assertThat(userMsg).contains("【参考资料】");
            assertThat(userMsg).contains("参考文档：猫的习性");
        }

        @Test
        @DisplayName("Few-Shot JSON（input/output 格式）会展开为成对的 user/assistant 消息")
        void fewShot_inputOutputFormat() {
            PromptTemplate tpl = new PromptTemplate();
            tpl.setId(400L);
            tpl.setSystemPrompt("sys");
            tpl.setUserPromptTemplate("ask {{topic}}");
            tpl.setFewShotExamples("""
                    [
                      {"input": "样例输入1", "output": "样例输出1"},
                      {"input": "样例输入2", "output": "样例输出2"}
                    ]
                    """);
            when(templateLookup.findById(400L)).thenReturn(tpl);

            List<ChatMessage> messages = promptService.render(
                    400L, "XIAOHONGSHU", Map.of("topic", "猫"), null);

            // system + user1/assistant1 + user2/assistant2 + final user
            assertThat(messages).hasSize(6);
            assertThat(messages.get(0).getRole()).isEqualTo("system");
            assertThat(messages.get(1).getRole()).isEqualTo("user");
            assertThat(messages.get(1).getContent()).isEqualTo("样例输入1");
            assertThat(messages.get(2).getRole()).isEqualTo("assistant");
            assertThat(messages.get(2).getContent()).isEqualTo("样例输出1");
            assertThat(messages.get(3).getRole()).isEqualTo("user");
            assertThat(messages.get(4).getRole()).isEqualTo("assistant");
            assertThat(messages.get(5).getRole()).isEqualTo("user");
            assertThat(messages.get(5).getContent()).isEqualTo("ask 猫");
        }

        @Test
        @DisplayName("Few-Shot JSON（role/content 格式）按消息原样添加")
        void fewShot_roleContentFormat() {
            PromptTemplate tpl = new PromptTemplate();
            tpl.setId(500L);
            tpl.setSystemPrompt("sys");
            tpl.setUserPromptTemplate("hi");
            tpl.setFewShotExamples("""
                    [
                      {"role": "user", "content": "示例问"},
                      {"role": "assistant", "content": "示例答"}
                    ]
                    """);
            when(templateLookup.findById(500L)).thenReturn(tpl);

            List<ChatMessage> messages = promptService.render(
                    500L, "XIAOHONGSHU", Map.of(), null);

            assertThat(messages).hasSize(4);
            assertThat(messages.get(1).getRole()).isEqualTo("user");
            assertThat(messages.get(1).getContent()).isEqualTo("示例问");
            assertThat(messages.get(2).getRole()).isEqualTo("assistant");
            assertThat(messages.get(2).getContent()).isEqualTo("示例答");
        }

        @Test
        @DisplayName("Few-Shot JSON 解析失败时只发出警告，渲染流程不中断")
        void fewShot_invalidJson_doesNotBreakRender() {
            PromptTemplate tpl = new PromptTemplate();
            tpl.setId(600L);
            tpl.setSystemPrompt("sys");
            tpl.setUserPromptTemplate("hi");
            tpl.setFewShotExamples("not-a-valid-json");
            when(templateLookup.findById(600L)).thenReturn(tpl);

            List<ChatMessage> messages = promptService.render(
                    600L, "XIAOHONGSHU", Map.of(), null);

            // Few-Shot 解析失败但 system+user 还在
            assertThat(messages).hasSize(2);
        }
    }
}
