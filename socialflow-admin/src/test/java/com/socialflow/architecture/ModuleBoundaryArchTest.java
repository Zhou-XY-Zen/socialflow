package com.socialflow.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 模块边界架构规则测试 —— 防止 service-* 之间出现反向依赖造成循环。
 *
 * <p>当前依赖图（线性，无环）：</p>
 * <pre>
 * common → model → dao → ai, publish (并行) → core, codeanalysis (并行) → web → admin
 * </pre>
 *
 * <p>本测试在 CI 阶段强制保证：</p>
 * <ol>
 *   <li>service-ai 不能依赖 service-{core, codeanalysis, publish}</li>
 *   <li>service-publish 不能依赖任何其他 service-* 模块</li>
 *   <li>service-core / service-codeanalysis 可以依赖 service-ai，但不能互相依赖</li>
 *   <li>所有 service-* 都不能依赖 web 层（避免反向引用 Controller）</li>
 *   <li>model / dao / common 是叶子，不能反向引用 service-*</li>
 * </ol>
 *
 * <p>触发方式：每次 {@code mvn test} 在 admin 模块自动跑。失败信息会指出哪个类违反了规则，
 * 强制开发者要么调整代码、要么显式修改规则（修改规则需 PR review）。</p>
 *
 * <p>跑在 admin 模块，因为它是依赖图的终点，能"看见"所有 service-* 模块的类。</p>
 */
@DisplayName("模块边界架构规则")
class ModuleBoundaryArchTest {

    /** 全工程类（含测试外的 main 代码 + 第三方依赖；规则匹配只保留 com.socialflow.*）*/
    private static JavaClasses ALL_CLASSES;

    @BeforeAll
    static void importClasses() {
        ALL_CLASSES = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .importPackages("com.socialflow");
    }

    // ====================================================================
    // 1. service-ai 是叶子层，不能依赖任何其他业务包
    // ====================================================================

    @Test
    @DisplayName("service-ai 不能依赖 service.content / service.codeanalysis / service.publish")
    void aiMustNotDependOnDownstream() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..service.ai..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..service.content..",
                        "..service.codeanalysis..",
                        "..service.publish..",
                        "..service.knowledge..",
                        "..service.media..",
                        "..service.user..",
                        "..service.dashboard..");
        rule.check(ALL_CLASSES);
    }

    // ====================================================================
    // 2. service-publish 不依赖任何其他 service-* 业务包
    // ====================================================================

    @Test
    @DisplayName("service-publish 不能依赖 ai / content / codeanalysis 等")
    void publishMustBeLeaf() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..service.publish..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..service.ai..",
                        "..service.content..",
                        "..service.codeanalysis..",
                        "..service.knowledge..",
                        "..service.media..");
        rule.check(ALL_CLASSES);
    }

    // ====================================================================
    // 3. core 与 codeanalysis 不能互相依赖（防钻石）
    // ====================================================================

    @Test
    @DisplayName("service-codeanalysis 不能依赖 service-core 业务包")
    void codeanalysisMustNotDependOnCore() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..service.codeanalysis..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..service.content..",
                        "..service.knowledge..",
                        "..service.media..",
                        "..service.publish..",
                        "..service.dashboard..");
        rule.check(ALL_CLASSES);
    }

    @Test
    @DisplayName("service-core 业务包不能依赖 service-codeanalysis")
    void coreMustNotDependOnCodeanalysis() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                        "..service.content..",
                        "..service.knowledge..",
                        "..service.media..",
                        "..service.user..",
                        "..service.dashboard..")
                .should().dependOnClassesThat().resideInAPackage("..service.codeanalysis..");
        rule.check(ALL_CLASSES);
    }

    // ====================================================================
    // 4. 所有 service-* 都不能依赖 web 层（防止 Controller 反向耦合）
    // ====================================================================

    @Test
    @DisplayName("service-* 不能依赖 web.controller / web.aspect 等 Web 层")
    void serviceMustNotDependOnWeb() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..service..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..web.controller..",
                        "..web.aspect..",
                        "..web.handler..",
                        "..web.filter..");
        rule.check(ALL_CLASSES);
    }

    // ====================================================================
    // 5. model / dao / common 是底层，不能引用 service-*
    // ====================================================================

    @Test
    @DisplayName("model 包不能依赖 service / web / dao")
    void modelMustBeBottomLayer() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..model..")
                .and().resideOutsideOfPackages("..vo..")  // VO 视图模型不在限制内
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..service..",
                        "..web..",
                        "..dao..");
        rule.check(ALL_CLASSES);
    }

    @Test
    @DisplayName("dao 包不能依赖 service / web 层")
    void daoMustNotDependOnService() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..dao..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..service..",
                        "..web..");
        rule.check(ALL_CLASSES);
    }

    @Test
    @DisplayName("common 包不能依赖任何业务包（最底层）")
    void commonMustBeBottomMost() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..common..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..service..",
                        "..web..",
                        "..dao..",
                        "..model.entity..",
                        "..model.dto..",
                        "..model.vo..");
        rule.check(ALL_CLASSES);
    }

    // ====================================================================
    // 6. 命名规范守卫
    // ====================================================================
    //
    // 说明：原本想加"ServiceImpl 必须在 impl 子包"的规则，但工程历史里有 8 个 Impl 类
    // 跟接口同包（embedding / eval / memory / prompt / media 这几处），属于既存现实，
    // 重排会引发包名变化，跨模块影响面较大。当前优先保留"模块边界"和"分层"这两类硬约束，
    // 命名规范留给 IDE 检查或后续单独整理。

    @Test
    @DisplayName("Mapper 接口必须放在 dao.mapper 包")
    void mappersLiveInMapperPackage() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Mapper")
                .and().resideInAPackage("com.socialflow..")
                .should().resideInAPackage("..dao.mapper..");
        rule.check(ALL_CLASSES);
    }
}
