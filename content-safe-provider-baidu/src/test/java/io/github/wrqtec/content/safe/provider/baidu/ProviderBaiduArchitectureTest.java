package io.github.wrqtec.content.safe.provider.baidu;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * provider-baidu 依赖方向守护
 *
 * @author L.David
 */
class ProviderBaiduArchitectureTest {

    private static final String BASE_PACKAGE = "io.github.wrqtec.content.safe.provider.baidu";

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);

    @Test
    void gsonOnlyUsedInsideInternalPackage() {
        ArchRule rule = noClasses().that().resideOutsideOfPackage(BASE_PACKAGE + ".internal..")
                .should().dependOnClassesThat().resideInAnyPackage("com.google.gson..");
        rule.check(classes);
    }

    @Test
    void doesNotDependOnCoreOrStarter() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE_PACKAGE + "..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "io.github.wrqtec.content.safe.core..",
                        "io.github.wrqtec.content.safe.starter..");
        rule.check(classes);
    }
}
