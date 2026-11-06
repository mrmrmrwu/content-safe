package io.github.wrqtec.content.safe.provider.tencent;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * provider-tencent 模块架构守护（铁律 §3/§9）：
 * SDK 类型只允许出现在 internal 包；provider 模块不得依赖 core/starter
 *
 * @author L.David
 */
@AnalyzeClasses(packages = "io.github.wrqtec.content.safe.provider.tencent",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ProviderTencentArchitectureTest {

    @ArchTest
    static final ArchRule sdkTypesOnlyInInternalPackage = noClasses()
            .that().resideOutsideOfPackage("..tencent.internal..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.tencentcloudapi..");

    @ArchTest
    static final ArchRule noCoreOrStarterDependency = noClasses()
            .that().resideInAPackage("..tencent..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..content.safe.core..", "..content.safe.starter..");
}
