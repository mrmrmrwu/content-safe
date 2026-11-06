package io.github.wrqtec.content.safe.core;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class CoreArchitectureTest {

    private static final JavaClasses CLASSES =
            new ClassFileImporter().importPackages("io.github.wrqtec.content.safe.core");

    @Test
    void coreMustNotDependOnSpringOrCloudSdk() {
        ArchRule rule = noClasses().that().resideInAPackage("..core..")
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
    void coreMustNotDependOnStarter() {
        ArchRule rule = noClasses().that().resideInAPackage("..core..")
                .should().dependOnClassesThat()
                .resideInAPackage("io.github.wrqtec.content.safe.starter..");
        rule.check(CLASSES);
    }
}
