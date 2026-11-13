package io.github.ilyaslabs.evl.core;

import static io.github.ilyaslabs.evl.core.TestSupport.codes;
import static io.github.ilyaslabs.evl.core.TestSupport.load;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.ilyaslabs.evl.core.validate.SecretScanner;
import org.junit.jupiter.api.Test;

class ValidationRulesTest {

    @Test
    void sensitiveDefault() {
        assertThat(codes(load("""
            configProperties:
              - { id: config.db.password, key: db.password, sensitive: true, defaultValue: hunter2 }
            """))).contains("EVL-E070");
    }

    @Test
    void secrets() {
        assertThat(SecretScanner.scan("AKIAABCDEFGHIJKLMNOP")).isPresent();
        assertThat(SecretScanner.scan("-----BEGIN RSA PRIVATE KEY-----")).isPresent();
        assertThat(SecretScanner.scan("password=s3cr3tValue")).isPresent();
        assertThat(SecretScanner.scan("password=${DB_PASSWORD}")).isEmpty();
        assertThat(SecretScanner.scan("Reserves stock before payment")).isEmpty();
        assertThat(SecretScanner.scan("commit 3f2a9c1e0b7d4a6f8e2c1b0a9d8e7f6a5b4c3d2e")).isEmpty();
        assertThat(codes(load("""
            components:
              - { id: c, name: C, description: "token ghp_abcdefghijklmnopqrstuvwxyzABCDEFGHIJ" }
            """))).contains("EVL-E071");
    }

    @Test
    void modulePackages() {
        assertThat(codes(load("""
            modules:
              - { id: order, name: Order, packages: ["com.example.order.**"] }
            components:
              - { id: c, name: C, module: order, source: { ref: com.example.billing.Invoice } }
            """))).contains("EVL-W080");
    }

    @Test
    void missingNameAndCategory() {
        assertThat(codes(load("flows:\n  - { id: f }\n"))).contains("EVL-E106");
    }
}
