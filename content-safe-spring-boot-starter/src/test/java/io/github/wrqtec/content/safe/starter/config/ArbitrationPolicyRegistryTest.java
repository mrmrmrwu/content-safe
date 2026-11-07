package io.github.wrqtec.content.safe.starter.config;

import java.util.List;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.spi.AuditArbitrationPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArbitrationPolicyRegistryTest {

    private static AuditArbitrationPolicy policy(String name) {
        return new AuditArbitrationPolicy() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public AuditResult arbitrate(List<AuditResult> results) {
                return results.get(0);
            }
        };
    }

    private final AuditArbitrationPolicy firstNonPass = policy("FIRST_NON_PASS");
    private final AuditArbitrationPolicy vote = policy("VOTE");

    @Test
    void sceneOverrideWinsOverDefault() {
        ContentSafeProperties properties = new ContentSafeProperties();
        properties.getDefaults().setArbitration("FIRST_NON_PASS");
        ContentSafeProperties.SceneProperties scene = new ContentSafeProperties.SceneProperties();
        scene.setDefaultArbitration("vote");
        properties.getScenes().put("comment", scene);

        ArbitrationPolicyRegistry registry =
                new ArbitrationPolicyRegistry(List.of(firstNonPass, vote), properties);

        assertSame(vote, registry.forScene("comment"));
        assertSame(firstNonPass, registry.forScene("nickname"));
        assertEquals("vote", registry.policyNameFor("comment"));
        assertEquals("FIRST_NON_PASS", registry.policyNameFor("nickname"));
    }

    @Test
    void nameMatchingIsCaseAndDashInsensitive() {
        ContentSafeProperties properties = new ContentSafeProperties();
        properties.getDefaults().setArbitration("first-non-pass");
        ArbitrationPolicyRegistry registry =
                new ArbitrationPolicyRegistry(List.of(firstNonPass), properties);
        assertSame(firstNonPass, registry.forScene("any"));
    }

    @Test
    void unknownPolicyNameFailsAtStartup() {
        ContentSafeProperties properties = new ContentSafeProperties();
        properties.getDefaults().setArbitration("NOPE");
        assertThrows(ConfigInvalidException.class,
                () -> new ArbitrationPolicyRegistry(List.of(firstNonPass), properties));
    }

    @Test
    void unknownScenePolicyNameFailsAtStartup() {
        ContentSafeProperties properties = new ContentSafeProperties();
        ContentSafeProperties.SceneProperties scene = new ContentSafeProperties.SceneProperties();
        scene.setDefaultArbitration("NOPE");
        properties.getScenes().put("comment", scene);
        assertThrows(ConfigInvalidException.class,
                () -> new ArbitrationPolicyRegistry(List.of(firstNonPass, vote), properties));
    }

    @Test
    void offMeansNoArbitrationForScene() {
        ContentSafeProperties properties = new ContentSafeProperties();
        properties.getDefaults().setArbitration(ArbitrationPolicyRegistry.OFF);
        ContentSafeProperties.SceneProperties scene = new ContentSafeProperties.SceneProperties();
        scene.setDefaultArbitration("none");
        properties.getScenes().put("comment", scene);

        ArbitrationPolicyRegistry registry =
                new ArbitrationPolicyRegistry(List.of(firstNonPass), properties);

        // OFF/NONE 表示该场景走主备 failover，不仲裁
        assertNull(registry.forScene("nickname"));
        assertNull(registry.forScene("comment"));
    }

    @Test
    void blankArbitrationMeansNoArbitration() {
        ContentSafeProperties properties = new ContentSafeProperties();
        properties.getDefaults().setArbitration("");
        ArbitrationPolicyRegistry registry =
                new ArbitrationPolicyRegistry(List.of(firstNonPass), properties);
        assertNull(registry.forScene("nickname"));
    }
}
