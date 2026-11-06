package io.github.wrqtec.content.safe.provider.aliyun;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * provider-aliyun 模块架构约束测试（ArchUnit）：
 * 云 SDK 类型只允许出现在 internal 包（铁律 §3.4：云 SDK 类型不得出现在对外签名）；
 * 模块不得反向依赖 core / starter
 *
 * @author L.David
 */
class ProviderAliyunArchitectureTest {

    private static final com.tngtech.archunit.core.domain.JavaClasses CLASSES =
            new ClassFileImporter().importPackages("io.github.wrqtec.content.safe.provider.aliyun");

    @Test
    void cloudSdkTypesConfinedToInternalPackage() {
        ArchRule rule = noClasses().that().resideOutsideOfPackage("..aliyun.internal..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.aliyun..", "com.aliyuncs..", "com.aliyun.tea..");
        rule.check(CLASSES);
    }

    @Test
    void mustNotDependOnCoreOrStarter() {
        ArchRule rule = noClasses().that().resideInAPackage("..provider.aliyun..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "io.github.wrqtec.content.safe.core..",
                        "io.github.wrqtec.content.safe.starter..");
        rule.check(CLASSES);
    }
}
