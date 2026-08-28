package com.alitycs.sdk;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class JavaInteropTest {
    @Test
    void exposesJavaFriendlyConstructorsAndOverloads() throws Exception {
        AlitycsConfig config = new AlitycsConfig("pk_java_test");
        Alitycs analytics = Alitycs.init(config);

        analytics.track("");
        analytics.track("scoped", java.util.Collections.emptyMap(), new EventOptions("usr_java"));
        analytics.identify("");
        analytics.captureError("");
        analytics.reset();

        assertNotNull(Alitycs.class.getMethod("page"));
        assertNotNull(EventOptions.class.getConstructor(String.class));
        assertNotNull(RevenuePayload.class.getMethod("transaction", String.class, String.class, String.class));
        analytics.shutdownBlocking();
    }
}
