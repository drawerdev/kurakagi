package io.github.drawerdev.kurakagi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Runs the hand-written test runners from {@code gradlew test}.
 * Each runner prints one [PASS]/[FAIL] line per check; the full output is in the test report
 * (app/build/reports/tests/ and app/build/test-results/).
 */
public class HandWrittenTests {

    @Test
    public void coreTestRunner() {
        int[] result = CoreTestRunner.run();
        System.out.println("CoreTestRunner: run=" + result[0] + " failed=" + result[1]);
        assertTrue("CoreTestRunner ran no checks", result[0] > 0);
        assertEquals("CoreTestRunner failures (see the [FAIL] lines in the output)", 0, result[1]);
    }

    @Test
    public void comprehensiveVerificationTest() {
        int[] result = ComprehensiveVerificationTest.run();
        System.out.println("ComprehensiveVerificationTest: run=" + result[0] + " failed=" + result[1]);
        assertTrue("ComprehensiveVerificationTest ran no checks", result[0] > 0);
        assertEquals("ComprehensiveVerificationTest failures (see the [FAIL] lines in the output)",
                0, result[1]);
    }
}
