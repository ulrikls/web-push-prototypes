package delta.app;

import java.time.Instant;

public record Message(Instant timestamp, Long nanoTime, String payload) {
}
