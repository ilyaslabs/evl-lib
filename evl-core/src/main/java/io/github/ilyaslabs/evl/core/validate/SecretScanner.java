package io.github.ilyaslabs.evl.core.validate;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects text that looks like a secret (rule EVL-E071). The same checks run in the processor,
 * the CLI and the plugin, and the matched value is never echoed in messages.
 */
public final class SecretScanner {

    private record Check(String what, Pattern pattern) {
    }

    private static final List<Check> CHECKS = List.of(
        new Check("a private key", Pattern.compile("-----BEGIN ([A-Z]+ )?PRIVATE KEY-----")),
        new Check("an AWS access key", Pattern.compile("\\b(AKIA|ASIA)[0-9A-Z]{16}\\b")),
        new Check("a GitHub token", Pattern.compile("\\b(ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{36}\\b|\\bgithub_pat_[A-Za-z0-9_]{60,}")),
        new Check("a Slack token", Pattern.compile("\\bxox[abprs]-[A-Za-z0-9-]{10,}")),
        new Check("a Stripe key", Pattern.compile("\\b[sr]k_live_[A-Za-z0-9]{16,}")),
        new Check("a Google API key", Pattern.compile("\\bAIza[0-9A-Za-z_-]{35}\\b")),
        new Check("a JSON web token", Pattern.compile("\\beyJ[A-Za-z0-9_-]{10,}\\.eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}")),
        new Check("credentials in a URL", Pattern.compile("[a-z][a-z0-9+.-]*://[^/\\s:@]+:[^/\\s@]+@")),
        new Check("a password assignment",
            Pattern.compile("(?i)\\b(password|passwd|pwd|secret|api[_-]?key|access[_-]?token|client[_-]?secret)\\s*[=:]\\s*['\"]?([^\\s'\"]{6,})")));

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9+/_=-]{32,}");
    private static final Set<String> PLACEHOLDERS = Set.of("changeme", "<secret>", "${", "xxxxxx", "******", "example", "placeholder");

    private SecretScanner() {
    }

    /** Returns what kind of secret the text appears to contain, if any. */
    public static Optional<String> scan(String text) {
        if (text == null || text.length() < 8) {
            return Optional.empty();
        }
        for (Check c : CHECKS) {
            Matcher m = c.pattern().matcher(text);
            while (m.find()) {
                if (c.what().equals("a password assignment")) {
                    String value = m.group(2).toLowerCase();
                    if (PLACEHOLDERS.stream().anyMatch(value::contains) || value.startsWith("$") || value.startsWith("<")) {
                        continue;
                    }
                }
                return Optional.of(c.what());
            }
        }
        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            String t = m.group();
            if (looksRandom(t)) {
                return Optional.of("a high-entropy token");
            }
        }
        return Optional.empty();
    }

    static boolean looksRandom(String t) {
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        for (char c : t.toCharArray()) {
            upper |= Character.isUpperCase(c);
            lower |= Character.isLowerCase(c);
            digit |= Character.isDigit(c);
        }
        if (!(digit && (upper || lower)) || (!upper && t.matches("[0-9a-f-]+") && t.length() <= 40)) {
            return false; // hex ids like commit hashes and UUIDs are common in docs
        }
        return entropy(t) >= 4.2;
    }

    static double entropy(String s) {
        int[] counts = new int[128];
        int n = 0;
        for (char c : s.toCharArray()) {
            if (c < 128) {
                counts[c]++;
                n++;
            }
        }
        double h = 0;
        for (int c : counts) {
            if (c > 0) {
                double p = (double) c / n;
                h -= p * (Math.log(p) / Math.log(2));
            }
        }
        return h;
    }
}
