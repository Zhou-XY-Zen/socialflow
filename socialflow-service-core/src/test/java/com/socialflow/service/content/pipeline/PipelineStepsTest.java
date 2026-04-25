package com.socialflow.service.content.pipeline;

import com.socialflow.common.exception.GuardrailException;
import com.socialflow.model.dto.ContentGenerateDTO;
import com.socialflow.service.ai.guardrails.GuardrailService;
import com.socialflow.service.ai.rag.RagPipelineService;
import com.socialflow.service.content.pipeline.steps.InputGuardrailStep;
import com.socialflow.service.content.pipeline.steps.RagRetrievalStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link InputGuardrailStep} 和 {@link RagRetrievalStep} 的单元测试。
 *
 * <p>验证两个 step 各自的契约：</p>
 * <ul>
 *   <li>InputGuardrailStep —— 把 dto.topic 透传到 GuardrailService.checkInput；
 *       GuardrailException 直接向上抛中止 pipeline</li>
 *   <li>RagRetrievalStep —— kbId 为 null 时 short-circuit 不调 RAG；
 *       有 kbId 时把检索结果写入 ctx.ragContext</li>
 *   <li>order/name 元数据正确</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Pipeline 内置 steps")
class PipelineStepsTest {

    private GenerationContext newCtx(ContentGenerateDTO dto) {
        return new GenerationContext(99L, dto);
    }

    // ====================================================================
    // InputGuardrailStep
    // ====================================================================

    @org.junit.jupiter.api.Nested
    @DisplayName("InputGuardrailStep")
    class InputGuardrail {

        @Mock
        private GuardrailService guardrailService;

        @InjectMocks
        private InputGuardrailStep step;

        @Test
        @DisplayName("name = 'InputGuardrail'，order = 10")
        void metadata() {
            assertThat(step.name()).isEqualTo("InputGuardrail");
            assertThat(step.order()).isEqualTo(10);
        }

        @Test
        @DisplayName("apply 把 userId/topic 透传给 GuardrailService.checkInput")
        void delegatesToGuardrailService() {
            ContentGenerateDTO dto = new ContentGenerateDTO();
            dto.setTopic("互联网创业");

            step.apply(newCtx(dto));

            verify(guardrailService).checkInput(99L, "互联网创业");
        }

        @Test
        @DisplayName("guardrail 抛 GuardrailException 时，本 step 直接向上抛")
        void propagatesGuardrailException() {
            ContentGenerateDTO dto = new ContentGenerateDTO();
            dto.setTopic("非法内容");
            doThrow(new GuardrailException("SENSITIVE_WORD", "INPUT", "命中敏感词"))
                    .when(guardrailService).checkInput(anyLong(), anyString());

            assertThatThrownBy(() -> step.apply(newCtx(dto)))
                    .isInstanceOf(GuardrailException.class)
                    .hasMessageContaining("命中敏感词");
        }
    }

    // ====================================================================
    // RagRetrievalStep
    // ====================================================================

    @org.junit.jupiter.api.Nested
    @DisplayName("RagRetrievalStep")
    class RagRetrieval {

        @Mock
        private RagPipelineService ragPipelineService;

        @InjectMocks
        private RagRetrievalStep step;

        @Test
        @DisplayName("name = 'RagRetrieval'，order = 20")
        void metadata() {
            assertThat(step.name()).isEqualTo("RagRetrieval");
            assertThat(step.order()).isEqualTo(20);
        }

        @Test
        @DisplayName("kbId 为 null 时跳过：不调 RAG，ctx.ragContext 保持 null")
        void skipsWhenNoKb() {
            ContentGenerateDTO dto = new ContentGenerateDTO();
            dto.setTopic("话题");
            // 注意没设 kbId
            GenerationContext ctx = newCtx(dto);

            step.apply(ctx);

            verify(ragPipelineService, never()).retrieveAsContext(
                    anyLong(), anyLong(), anyString(), anyInt());
            assertThat(ctx.getRagContext()).isNull();
        }

        @Test
        @DisplayName("有 kbId 时调用 RAG 并写入 ctx.ragContext")
        void retrievesWhenKbPresent() {
            ContentGenerateDTO dto = new ContentGenerateDTO();
            dto.setKbId(7L);
            dto.setTopic("咖啡");
            when(ragPipelineService.retrieveAsContext(99L, 7L, "咖啡", 5))
                    .thenReturn("RAG 文档片段……");

            GenerationContext ctx = newCtx(dto);
            step.apply(ctx);

            assertThat(ctx.getRagContext()).isEqualTo("RAG 文档片段……");
        }
    }
}
