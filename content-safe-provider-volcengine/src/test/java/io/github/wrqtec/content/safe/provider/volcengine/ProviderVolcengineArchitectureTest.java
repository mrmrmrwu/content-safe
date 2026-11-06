package io.github.wrqtec.content.safe.provider.volcengine;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * provider-volcengine 模块架构守护（铁律 §3/§9）：
 * SDK 类型只允许出现在 internal 包；provider 模块不得依赖 core/starter
 *
 * @author L.David
 */
@AnalyzeClasses(packages = "io.github.wrqtec.content.safe.provider.volcengine",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ProviderVolcengineArchitectureTest {

    @ArchTest
    static final ArchRule sdkTypesOnlyInInternalPackage = noClasses()
            .that().resideOutsideOfPackage("..volcengine.internal..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.volcengine..");

    @ArchTest
    static final ArchRule noCoreOrStarterDependency = noClasses()
            .that().resideInAPackage("..volcengine..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..content.safe.core..", "..content.safe.starter..");
}
