package thorization.ai;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AiEvaluationCacheTest {

    @Test
    void testHashDeterminism() {
        String hash1 = AiEvaluationCache.computeHash(
                "GET",
                "https://api.example.com/v1/users/123",
                "{\"tenant_id\": 10}",
                "{\"name\": \"Alice\"}",
                "Authorization: Bearer attacker-token",
                "{\"name\": \"Alice\"}",
                "gpt-4o-mini"
        );

        String hash2 = AiEvaluationCache.computeHash(
                "GET",
                "https://api.example.com/v1/users/123",
                "{\"tenant_id\": 10}",
                "{\"name\": \"Alice\"}",
                "Authorization: Bearer attacker-token",
                "{\"name\": \"Alice\"}",
                "gpt-4o-mini"
        );

        assertNotNull(hash1);
        assertEquals(64, hash1.length());
        assertTrue(hash1.matches("^[a-f0-9]{64}$"));
        assertEquals(hash1, hash2);
    }

    @Test
    void testHashSensitivity() {
        String base = AiEvaluationCache.computeHash("GET", "https://api.com/u", "b", "r", "h", "rr", "m");

        assertNotEquals(base, AiEvaluationCache.computeHash("POST", "https://api.com/u", "b", "r", "h", "rr", "m"));
        assertNotEquals(base, AiEvaluationCache.computeHash("GET", "https://api.com/v2/u", "b", "r", "h", "rr", "m"));
        assertNotEquals(base, AiEvaluationCache.computeHash("GET", "https://api.com/u", "different_body", "r", "h", "rr", "m"));
        assertNotEquals(base, AiEvaluationCache.computeHash("GET", "https://api.com/u", "b", "diff_resp", "h", "rr", "m"));
        assertNotEquals(base, AiEvaluationCache.computeHash("GET", "https://api.com/u", "b", "r", "diff_hdr", "rr", "m"));
        assertNotEquals(base, AiEvaluationCache.computeHash("GET", "https://api.com/u", "b", "r", "h", "diff_rresp", "m"));
        assertNotEquals(base, AiEvaluationCache.computeHash("GET", "https://api.com/u", "b", "r", "h", "rr", "deepseek-chat"));
    }

    @Test
    void testCachePutAndGetHit() {
        AiEvaluationCache cache = new AiEvaluationCache(100, Duration.ofMinutes(10));
        String hash = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

        AiEvaluationResult original = AiEvaluationResult.success(
                AiVerdict.VALIDATED_ACCESS,
                "Vulnerability confirmed",
                "gpt-4o-mini",
                500L
        );

        cache.put(hash, original);

        AiEvaluationResult hit = cache.get(hash);
        assertNotNull(hit);
        assertEquals(AiVerdict.VALIDATED_ACCESS, hit.getVerdict());
        assertEquals("Vulnerability confirmed", hit.getReasoning());
        assertTrue(hit.isCached());
        assertEquals(0L, hit.getExecutionTimeMs());
    }

    @Test
    void testCacheMissReturnsNull() {
        AiEvaluationCache cache = new AiEvaluationCache();
        assertNull(cache.get("non-existent-hash"));
        assertNull(cache.get(null));
    }

    @Test
    void testCacheEviction() {
        // Cache with max capacity 3
        AiEvaluationCache cache = new AiEvaluationCache(3, Duration.ofMinutes(10));

        for (int i = 0; i < 10; i++) {
            String hash = "hash_" + i;
            cache.put(hash, AiEvaluationResult.pending("model"));
        }

        assertTrue(cache.size() <= 3, "Cache size should not exceed 3 but was " + cache.size());
    }

    @Test
    void testCacheClear() {
        AiEvaluationCache cache = new AiEvaluationCache();
        cache.put("h1", AiEvaluationResult.pending("m"));
        cache.put("h2", AiEvaluationResult.pending("m"));
        assertEquals(2, cache.size());

        cache.clear();
        assertEquals(0, cache.size());
        assertNull(cache.get("h1"));
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        AiEvaluationCache cache = new AiEvaluationCache(1000, Duration.ofMinutes(10));
        int threadCount = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successOps = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        String hash = "hash_" + (i % 20);
                        cache.put(hash, AiEvaluationResult.success(AiVerdict.ACCESS_DENIED, "Denied", "m", 100));
                        AiEvaluationResult res = cache.get(hash);
                        if (res != null) {
                            successOps.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        assertTrue(successOps.get() > 0);
    }
}
