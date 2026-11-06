package io.github.wrqtec.content.safe.api;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 模块铁律守护：api 只允许依赖 JDK，不得依赖 Spring、云厂商 SDK、HTTP/JSON 框架
 *
 * @author L.David
 */
@AnalyzeClasses(packages = "io.github.wrqtec.content.safe.api",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ApiArchitectureTest {

    @ArchTest
    static final ArchRule API_MUST_NOT_DEPEND_ON_FRAMEWORKS_OR_CLOUD_SDK = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.servlet..",
                    "com.aliyun..",
                    "com.aliyuncs..",
                    "com.tencentcloudapi..",
                    "com.baidubce..",
                    "com.volcengine..",
                    "com.ishumei..",
                    "com.fasterxml.jackson..",
                    "com.google.gson..",
                    "okhttp3..",
                    "org.apache.http..")
            .because("api 模块只允许依赖 JDK，保证业务方引入零传递依赖（见 CLAUDE.md 模块铁律）");
}
