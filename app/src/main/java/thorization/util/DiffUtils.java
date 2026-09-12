package thorization.util;

/**
 * Utility for computing text similarity and differences.
 */
public class DiffUtils {

    /**
     * Computes Sorensen-Dice coefficient similarity score between two strings (0.0 to 1.0).
     */
    public static double computeSimilarity(String s1, String s2) {
        if (s1 == null && s2 == null) return 1.0;
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equals(s2)) return 1.0;

        int len1 = s1.length();
        int len2 = s2.length();
        if (len1 < 2 || len2 < 2) return 0.0;

        // Bigram matching for fast O(N) similarity estimation
        java.util.Map<String, Integer> profile1 = getBigramProfile(s1);
        java.util.Map<String, Integer> profile2 = getBigramProfile(s2);

        int intersection = 0;
        for (java.util.Map.Entry<String, Integer> entry : profile1.entrySet()) {
            String bigram = entry.getKey();
            int count1 = entry.getValue();
            int count2 = profile2.getOrDefault(bigram, 0);
            intersection += Math.min(count1, count2);
        }

        int totalBigrams = (len1 - 1) + (len2 - 1);
        return (2.0 * intersection) / totalBigrams;
    }

    private static java.util.Map<String, Integer> getBigramProfile(String s) {
        java.util.Map<String, Integer> profile = new java.util.HashMap<>();
        int len = Math.min(s.length(), 20000); // Limit size for high throughput
        for (int i = 0; i < len - 1; i++) {
            String bigram = s.substring(i, i + 2);
            profile.put(bigram, profile.getOrDefault(bigram, 0) + 1);
        }
        return profile;
    }

    /**
     * Calculates percentage difference in body length relative to original length.
     */
    public static double calculateLengthDiffPercent(int originalLength, int replayedLength) {
        if (originalLength == 0 && replayedLength == 0) return 0.0;
        if (originalLength == 0) return 100.0;
        return (Math.abs((double) replayedLength - originalLength) / originalLength) * 100.0;
    }
}

