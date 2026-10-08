package pl.ngo.budget.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/** Blokuje na chwilę kolejne próby po serii błędów (np. zgadywanie obecnego hasła). */
@Component
public class FailedAttemptLimiter {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK = Duration.ofMinutes(15);

    private record State(int failures, Instant lockedUntil) {}

    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        State state = states.get(normalize(key));
        return state != null && state.lockedUntil() != null && Instant.now().isBefore(state.lockedUntil());
    }

    public void recordFailure(String key) {
        states.compute(normalize(key), (k, old) -> {
            boolean expired = old != null && old.lockedUntil() != null && !Instant.now().isBefore(old.lockedUntil());
            int failures = (old == null || expired ? 0 : old.failures()) + 1;
            return failures >= MAX_FAILURES ? new State(0, Instant.now().plus(LOCK)) : new State(failures, null);
        });
    }

    public void reset(String key) {
        states.remove(normalize(key));
    }

    private static String normalize(String key) {
        return key == null ? "" : key.toLowerCase(Locale.ROOT);
    }
}
