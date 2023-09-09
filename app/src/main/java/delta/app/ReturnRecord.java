package delta.app;

import java.time.Instant;

public record ReturnRecord(Instant timestamp, String protocol, Long nanoTime) {
}
