/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package delta.app;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import io.javalin.plugin.bundled.CorsPluginConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Level.SEVERE;
import static org.eclipse.jetty.http.HttpHeader.CACHE_CONTROL;
import static org.eclipse.jetty.http.HttpHeaderValue.NO_CACHE;

public class App {

    public static void main(String[] args) {
        int payloadLength = (args.length > 0) ? Integer.parseInt(args[0]) : 0;
        new App(payloadLength).start();
    }

    private final Javalin app;
    private final Logger logger = Logger.getLogger(App.class.getName());

    private final Random random = new Random();
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    private final Queue<ReturnRecord> logReplies = new ConcurrentLinkedQueue<>();

    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH.mm.ss").withZone(ZoneId.systemDefault());
    private final Path csvFile;
    private final String payload;
    private final long messageIntervalMs = 1000;


    public App(int payloadLength) {
        csvFile = Path.of("LogReply-" + Instant.now().toString().replace(':', '.') + "-" + payloadLength + ".csv");
        payload = randomPayload(payloadLength);

        app = Javalin.create(config -> {
            config.plugins.enableCors(cors -> cors.add(CorsPluginConfig::anyHost));
            config.staticFiles.add("/clients");
            config.jsonMapper(new JavalinJackson().updateMapper(
                    mapper -> mapper.configure(WRITE_DATES_AS_TIMESTAMPS, false)));
        });

        configureReturn();

        configureLongPolling();
        configureServerSentEvents();
        configureWebsocket();
    }

    private static String randomPayload(int payloadLength) {
        var bytes = new byte[payloadLength];
        new Random().nextBytes(bytes);
        return new String(bytes, UTF_8);
    }

    public void start() {
        app.start(7070);

        executor.scheduleAtFixedRate(this::writeLogReplyToCSV, 10L, 30L, SECONDS);
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

    private void configureLongPolling() {
        app.post("/lp", ctx -> {
            ctx.header(CACHE_CONTROL.toString(), NO_CACHE.toString());
            var lpFuture = new CompletableFuture<Message>();
            ctx.future(() -> lpFuture.thenAccept(ctx::json));
            executor.schedule(
                    () -> lpFuture.complete(createMessage()),
                    Math.round(messageIntervalMs + random.nextGaussian() * 100),
                    MILLISECONDS);
        });
    }

    private void configureServerSentEvents() {
        app.sse("/sse", sseClient -> {
            sseClient.keepAlive();
            executor.scheduleAtFixedRate(
                    () -> {
                        if (!sseClient.terminated()) {
                            sseClient.sendEvent(createMessage());
                        }
                    },
                    random.nextLong(0, messageIntervalMs),
                    messageIntervalMs,
                    MILLISECONDS);
        });
    }

    private void configureWebsocket() {
        app.ws("/ws", ws -> ws.onConnect(wsClient -> executor.scheduleAtFixedRate(
                () -> {
                    if (wsClient.session.isOpen()) {
                        wsClient.send(createMessage());
                    }
                },
                random.nextLong(0, messageIntervalMs),
                messageIntervalMs,
                MILLISECONDS)));
    }

    private Message createMessage() {
        return new Message(
                Instant.now(),
                System.nanoTime(),
                payload);
    }


    private void writeLogReplyToCSV() {
        try (var writer = Files.newBufferedWriter(csvFile, UTF_8, CREATE, APPEND)) {
            ReturnRecord logReply;
            while ((logReply = logReplies.poll()) != null) {
                writer.write(convertRecToLine(logReply));
            }
        } catch (IOException e) {
            logger.log(SEVERE, "Error writing latency to CSV file " + csvFile, e);
        }
    }

    private String convertRecToLine(ReturnRecord logReply) {
        return String.join(";",
                logReply.timestamp().toString(),
                dateTimeFormatter.format(logReply.timestamp()),
                logReply.protocol(),
                logReply.nanoTime().toString()
        ) + System.lineSeparator();
    }

}
