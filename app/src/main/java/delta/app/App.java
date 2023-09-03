/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package delta.app;

import io.javalin.Javalin;
import io.javalin.http.sse.SseClient;
import io.javalin.plugin.bundled.CorsPluginConfig;
import io.javalin.websocket.WsContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class App {

    public static void main(String[] args) {
        new App().start();
    }

    private static final Path CSV_FILE = Path.of("LogReply.csv");

    private final Javalin app;

    private final Queue<SseClient> sseClients = new ConcurrentLinkedQueue<>();
    private final Queue<WsContext> wsClients = new ConcurrentLinkedQueue<>();
    private final Queue<CompletableFuture<Long>> lpClients = new ConcurrentLinkedQueue<>();

    private final Queue<ReturnRecord> logReplies = new ConcurrentLinkedQueue<>();


    public App() {
        app = Javalin.create(config -> config.plugins.enableCors(cors -> cors.add(CorsPluginConfig::anyHost)));

        configureReturn();
        configureServerSentEvents();
        configureWebsocket();
        configureLongPolling();
    }

    public void start() {
        app.start(7070);

        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(this::sendMessage, 0L, 1L, TimeUnit.SECONDS);
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(this::writeLogReplyToCSV, 10L, 30L, TimeUnit.SECONDS);
    }

    private void configureReturn() {
        app.post("/return", ctx -> {
            var currentTime = System.nanoTime();
            var record = ctx.bodyAsClass(ReturnRecord.class);

            logReplies.add(new ReturnRecord(
                    record.time(),
                    record.protocol(),
                    currentTime - record.nanoTime()));
        });
    }

    private void configureServerSentEvents() {
        app.sse("/sse", sseClient -> {
            sseClient.keepAlive();
            sseClient.onClose(() -> sseClients.remove(sseClient));
            sseClients.add(sseClient);
        });
    }

    private void configureWebsocket() {
        app.ws("/ws", ws -> {
            ws.onConnect(wsClients::add);
            ws.onClose(wsClients::remove);
        });
    }

    private void configureLongPolling() {
        app.post("/lp", ctx -> {
            CompletableFuture<Long> lpFuture = new CompletableFuture<>();
            lpClients.add(lpFuture);
            ctx.future(() -> lpFuture.thenAccept(response -> ctx.result(String.valueOf(response))));
        });
    }


    private void sendMessage() {
        sseClients.forEach(sseClient -> sseClient.sendEvent(System.nanoTime()));

        wsClients.forEach(wsClient -> wsClient.send(System.nanoTime()));

        CompletableFuture<Long> lpFuture;
        while ((lpFuture = lpClients.poll()) != null) {
            lpFuture.complete(System.nanoTime());
        }
    }


    private void writeLogReplyToCSV() {
        try (var writer = Files.newBufferedWriter(CSV_FILE, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            ReturnRecord logReply;
            while ((logReply = logReplies.poll()) != null) {
                writer.write(convertRecToLine(logReply));
            }
        } catch (IOException e) {
            throw new RuntimeException("Error writing latency to CSV file " + CSV_FILE, e);
        }
    }

    private String convertRecToLine(ReturnRecord logReply) {
        return String.join(";",
                logReply.time().toString(),
                logReply.protocol(),
                logReply.nanoTime().toString()
        ) + System.lineSeparator();
    }

}
