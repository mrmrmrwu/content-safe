package io.github.wrqtec.content.safe.spi;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * spi 模块架构约束测试（ArchUnit）：禁止依赖 Spring/云厂商 SDK/序列化框架与 core 模块
 *
 * @author L.David
 */
class SpiArchitectureTest {

    private static final com.tngtech.archunit.core.domain.JavaClasses CLASSES =
            new ClassFileImporter().importPackages("io.github.wrqtec.content.safe.spi");

    @Test
    void spiMustNotDependOnSpringOrCloudSdk() {
        ArchRule rule = noClasses().that().resideInAPackage("..spi..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "com.aliyun..", "com.aliyuncs..",
                        "com.tencentcloudapi..",
                        "com.baidubce..",
                        "com.volcengine..",
                        "com.ishumei..",
                        "com.fasterxml.jackson..",
                        "com.google.gson..");
        rule.check(CLASSES);
    }

    @Test
    void spiMustNotDependOnCore() {
        ArchRule rule = noClasses().that().resideInAPackage("..spi..")
                .should().dependOnClassesThat()
                .resideInAPackage("io.github.wrqtec.content.safe.core..");
        rule.check(CLASSES);
    }
}
