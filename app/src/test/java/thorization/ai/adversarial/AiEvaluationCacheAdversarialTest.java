package thorization.ai.adversarial;

import org.junit.jupiter.api.Test;
import thorization.ai.AiEvaluationCache;
import thorization.ai.AiEvaluationResult;
import thorization.ai.AiVerdict;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Adversarial stress and collision resistance verification for AiEvaluationCache.
 * Tests delimiter shifting, multi-byte UTF-8 length framing, massive concurrent races,
 * and memory-bounded eviction under load.
 */
class AiEvaluationCacheAdversarialTest {

    @Test
    void testBoundaryShiftingCollisionResistance() {
        // Test that shifting characters across boundaries does NOT produce hash collisions
        // 1. Method vs URL
        String h1 = AiEvaluationCache.computeHash("GET", "https://api.test/v1", "b", "r", "h", "rr", "m");
        String h2 = AiEvaluationCache.computeHash("GE", "Thttps://api.test/v1", "b", "r", "h", "rr", "m");
        assertNotEquals(h1, h2, "Method and URL boundary shift should not collide");

        // 2. Request body vs Original response
        String h3 = AiEvaluationCache.computeHash("POST", "/api", "ABC", "DEF", "h", "rr", "m");
        String h4 = AiEvaluationCache.computeHash("POST", "/api", "AB", "CDEF", "h", "rr", "m");
        assertNotEquals(h3, h4, "Request body and original response boundary shift should not collide");

        // 3. Role headers vs Role response
        String h5 = AiEvaluationCache.computeHash("POST", "/api", "b", "r", "X-Role: 1\n", "BODY", "m");
        String h6 = AiEvaluationCache.computeHash("POST", "/api", "b", "r", "X-Role: 1", "\nBODY", "m");
        assertNotEquals(h5, h6, "Headers and role response boundary shift should not collide");

        // 4. Role response vs Model
        String h7 = AiEvaluationCache.computeHash("POST", "/api", "b", "r", "h", "resp_gpt", "-4o-mini");
        String h8 = AiEvaluationCache.computeHash("POST", "/api", "b", "r", "h", "resp", "_gpt-4o-mini");
        assertNotEquals(h7, h8, "Role response and model boundary shift should not collide");
    }

    @Test
    void testDelimiterAndTagInjectionResistance() {
        // Attempt to forge another tag in the value
        // e.g., Inject "\nURL:4\0\0\0test\n" into METHOD
        String forgedPayload = "GET\nURL:4\0\0\0fake";
        String legitimateMethod = "GET";
        String legitimateUrl = "fake";

        String hashForged = AiEvaluationCache.computeHash(forgedPayload, "real_url", "b", "r", "h", "rr", "m");
        String hashLegit = AiEvaluationCache.computeHash(legitimateMethod, legitimateUrl, "b", "r", "h", "rr", "m");

        assertNotEquals(hashForged, hashLegit, "Injected tag framing inside value must not collide with legitimate fields");
    }

    @Test
    void testMultiByteUtf8AndEmojiHandling() {
        // String.length() is 2 for surrogate pair emoji, but UTF-8 byte length is 4 bytes
        String emoji1 = "🔒🔑🛡️";
        String emoji2 = "🔒🔑🛡️!";
        String chinese1 = "权限测试";
        String chinese2 = "权限验证";

        String hashEmoji1 = AiEvaluationCache.computeHash("POST", "/api", emoji1, "r", "h", "rr", "m");
        String hashEmoji2 = AiEvaluationCache.computeHash("POST", "/api", emoji2, "r", "h", "rr", "m");
        assertNotEquals(hashEmoji1, hashEmoji2);

        String hashCn1 = AiEvaluationCache.computeHash("POST", "/api", "b", chinese1, "h", "rr", "m");
        String hashCn2 = AiEvaluationCache.computeHash("POST", "/api", "b", chinese2, "h", "rr", "m");
        assertNotEquals(hashCn1, hashCn2);

        // Determinism test with multi-byte
        assertEquals(hashEmoji1, AiEvaluationCache.computeHash("POST", "/api", emoji1, "r", "h", "rr", "m"));
    }

    @Test
    void testNullVsEmptyStringHandling() {
        // computeHash treats null as empty byte array
        String hashNull = AiEvaluationCache.computeHash(null, null, null, null, null, null, null);
        String hashEmpty = AiEvaluationCache.computeHash("", "", "", "", "", "", "");

        assertEquals(hashNull, hashEmpty, "Null and empty string map cleanly to empty framing without throwing");
        assertNotNull(hashNull);
        assertEquals(64, hashNull.length());
    }

    @Test
    void testExtremeConcurrentStress() throws InterruptedException {
        int threads = 30;
        int opsPerThread = 200;
        int maxCapacity = 500;

        AiEvaluationCache cache = new AiEvaluationCache(maxCapacity, Duration.ofMinutes(5));
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successfulReads = new AtomicInteger(0);
        AtomicInteger successfulWrites = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < opsPerThread; i++) {
                        String hash = "hash_" + (i % 10);
                        if (i % 3 == 0) {
                            // Write
                            AiEvaluationResult res = AiEvaluationResult.success(
                                    (i % 2 == 0) ? AiVerdict.VALIDATED_ACCESS : AiVerdict.ACCESS_DENIED,
                                    "Reason " + i,
                                    "model-" + (threadId % 3),
                                    100L + i
                            );
                            cache.put(hash, res);
                            successfulWrites.incrementAndGet();
                        } else if (i % 7 == 0) {
                            // Compute hash dynamically
                            String dynHash = AiEvaluationCache.computeHash("GET", "/api/" + (i % 10), "b", "r", "h", "rr", "m");
                            cache.put(dynHash, AiEvaluationResult.pending("m"));
                            AiEvaluationResult dynItem = cache.get(dynHash);
                            if (dynItem != null) {
                                successfulReads.incrementAndGet();
                            }
                        } else if (i % 23 == 0) {
                            // Check stats
                            assertNotNull(cache.stats());
                        } else {
                            // Read
                            AiEvaluationResult item = cache.get(hash);
                            if (item != null) {
                                assertTrue(item.isCached());
                                assertEquals(0L, item.getExecutionTimeMs());
                                successfulReads.incrementAndGet();
                            }
                        }
                    }
                } catch (Throwable e) {
                    exceptions.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release all threads simultaneously
        startLatch.countDown();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS), "Concurrent stress test should finish within 15 seconds");
        executor.shutdown();

        assertTrue(exceptions.isEmpty(), "No concurrent exceptions should occur, but had: " + exceptions);
        assertTrue(successfulWrites.get() > 0, "Writes must have succeeded");
        assertTrue(successfulReads.get() > 0, "Reads must have succeeded");
        assertTrue(cache.size() <= maxCapacity, "Cache size must be <= capacity");
    }

    @Test
    void testCacheEvictionUnderContinuousSaturation() {
        int maxCapacity = 25;
        AiEvaluationCache cache = new AiEvaluationCache(maxCapacity, Duration.ofMinutes(1));

        // Insert 1,000 distinct entries
        for (int i = 0; i < 1000; i++) {
            String hash = "overflow_key_" + i;
            cache.put(hash, AiEvaluationResult.success(AiVerdict.ACCESS_DENIED, "Blocked", "m", 50));
        }

        // Guava cache eviction is amortized on read/write
        assertTrue(cache.size() <= maxCapacity, "Cache size should not exceed capacity " + maxCapacity + ", was: " + cache.size());
    }

    @Test
    void testDefensiveCopyAndImmutability() {
        AiEvaluationCache cache = new AiEvaluationCache(10, Duration.ofMinutes(1));
        String hash = "test-hash-immutable";

        AiEvaluationResult original = AiEvaluationResult.success(
                AiVerdict.VALIDATED_ACCESS,
                "Original reason",
                "model-v1",
                450L
        );

        cache.put(hash, original);

        AiEvaluationResult retrieved = cache.get(hash);
        assertNotNull(retrieved);
        assertTrue(retrieved.isCached(), "Retrieved item must report cached=true");
        assertEquals(0L, retrieved.getExecutionTimeMs(), "Retrieved item must have executionTimeMs=0");

        // Verify original item was NOT mutated
        assertFalse(original.isCached(), "Original result instance must not be modified");
        assertEquals(450L, original.getExecutionTimeMs(), "Original execution time must be preserved");
    }
}
