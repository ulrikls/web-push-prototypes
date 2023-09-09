/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package delta.app;

import com.fasterxml.jackson.databind.SerializationFeature;
import io.javalin.Javalin;
import io.javalin.http.sse.SseClient;
import io.javalin.json.JavalinJackson;
import io.javalin.plugin.bundled.CorsPluginConfig;
import io.javalin.websocket.WsContext;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpHeaderValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.*;

public class App {

    public static void main(String[] args) {
        int payloadLength = (args.length > 0) ? Integer.parseInt(args[0]) : 0;
        new App(payloadLength).start();
    }

    private static final Path CSV_FILE = Path.of("LogReply.csv");

    private final Javalin app;
    private final ExecutorService executor = Executors.newWorkStealingPool();

    private final Queue<SseClient> sseClients = new ConcurrentLinkedQueue<>();
    private final Queue<WsContext> wsClients = new ConcurrentLinkedQueue<>();
    private final Queue<CompletableFuture<Message>> lpClients = new ConcurrentLinkedQueue<>();

    private final Queue<ReturnRecord> logReplies = new ConcurrentLinkedQueue<>();

    private final String payload;


    public App(int payloadLength) {
        payload = randomPayload(payloadLength);

        app = Javalin.create(config -> {
            config.plugins.enableCors(cors -> cors.add(CorsPluginConfig::anyHost));
            config.staticFiles.add("/clients");
            config.jsonMapper(new JavalinJackson().updateMapper(
                    mapper -> mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)));
        });

        configureReturn();
        configureServerSentEvents();
        configureWebsocket();
        configureLongPolling();
    }

    private static String randomPayload(int payloadLength) {
        var bytes = new byte[payloadLength];
        new Random().nextBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
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
                    record.timestamp(),
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
            ctx.header(HttpHeader.CACHE_CONTROL.toString(), HttpHeaderValue.NO_CACHE.toString());
            CompletableFuture<Message> lpFuture = new CompletableFuture<>();
            lpClients.add(lpFuture);
            ctx.future(() -> lpFuture.thenAccept(ctx::json));
        });
    }


    private void sendMessage() {
        sseClients.forEach(sseClient -> executor.execute(() -> sseClient.sendEvent(createMessage())));

        wsClients.forEach(wsClient -> executor.execute(() -> wsClient.send(createMessage())));

        CompletableFuture<Message> lpFuture;
        while ((lpFuture = lpClients.poll()) != null) {
            lpFuture.complete(createMessage());
        }
    }

    private Message createMessage() {
        return new Message(
                Instant.now(),
                System.nanoTime(),
                payload);
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
                logReply.timestamp().toString(),
                logReply.protocol(),
                logReply.nanoTime().toString()
        ) + System.lineSeparator();
    }

}
