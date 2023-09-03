package delta.app;

import java.time.Instant;

public record ReturnRecord(Instant time, String protocol, Long nanoTime) {
}
