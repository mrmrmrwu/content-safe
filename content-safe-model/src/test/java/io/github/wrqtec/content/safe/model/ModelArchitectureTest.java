package io.github.wrqtec.content.safe.model;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * model 模块架构约束测试（ArchUnit）：禁止依赖 Spring/云厂商 SDK/序列化框架与 spi 模块
 *
 * @author L.David
 */
class ModelArchitectureTest {

    private static final com.tngtech.archunit.core.domain.JavaClasses CLASSES =
            new ClassFileImporter().importPackages("io.github.wrqtec.content.safe.model");

    @Test
    void modelMustNotDependOnSpringOrCloudSdk() {
        ArchRule rule = noClasses().that().resideInAPackage("..model..")
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
    void modelMustNotDependOnSpi() {
        ArchRule rule = noClasses().that().resideInAPackage("..model..")
                .should().dependOnClassesThat()
                .resideInAPackage("io.github.wrqtec.content.safe.spi..");
        rule.check(CLASSES);
    }
}
