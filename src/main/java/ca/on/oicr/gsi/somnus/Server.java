package ca.on.oicr.gsi.somnus;

import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import ca.on.oicr.gsi.status.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.common.TextFormat;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

@SuppressWarnings("restriction")
public final class Server implements ServerConfig {

  public static void main(String[] args) throws IOException {
    final Server server = new Server(Integer.parseInt(args[0]));
    readFile(args[1], server.knownEnvironments::add);
    readFile(args[2], server.knownServices::add);
    server.server.start();
  }

  private static void readFile(String filename, Consumer<? super String> consumer)
      throws IOException {
    try (final Stream<String> lines = Files.lines(Paths.get(filename), StandardCharsets.UTF_8)) {
      lines
          .filter(Pattern.compile("^[ \t]*(#.*)$").asPredicate().negate())
          .map(String::trim)
          .forEach(consumer);
    }
  }

  private static final LatencyHistogram responseTime =
      new LatencyHistogram(
          "somnus_http_request_time", "The time to respond to an HTTP request.", "url");
  private static final Gauge stopGauge =
      Gauge.build("somnus_inhibited", "Whether a service should be inhibited.")
          .labelNames("scope", "target_environment")
          .register();
  private final Deque<Inhibition> inhibitions = new ConcurrentLinkedDeque<>();
  private final Set<String> knownEnvironments = ConcurrentHashMap.newKeySet();
  private final Set<String> knownServices = ConcurrentHashMap.newKeySet();
  private Instant lastUpdate;
  private final ObjectMapper mapper = new ObjectMapper();
  private final HttpServer server;

  public Server(int port) throws IOException {
    mapper.registerModule(new JavaTimeModule());
    server = HttpServer.create(new InetSocketAddress(port), 0);
    add(
        "/",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody()) {
            new StatusPage(this) {

              @Override
              protected void emitCore(SectionRenderer renderer) {
                renderer.lineSpan("Last Updated", lastUpdate);
                renderer.line("Known Services", Integer.toString(knownServices.size()));
              }

              @Override
              public Stream<ConfigurationSection> sections() {
                return Stream.empty();
              }
            }.renderPage(os);
          }
        });
    add(
        "/view",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody()) {
            new BasePage(this, false) {

              @Override
              public Stream<Header> headers() {
                return Stream.of(
                    Header.jsModule("import {wake} from './ui.js'; window.wake = wake;"),
                    Header.css(
                        ".sleeping { background-color: #EAF5F7; } .button { color: #fff; background-color: #04AA9D; margin: 0.5em; padding: 0.2em; border: 1px solid #78C3CD; box-shadow: 0.1em 0.1em 5px 0px rgba(0,0,0,0.1); transition: all 0.3s ease 0s; cursor: pointer; display: inline-block; }"));
              }

              @Override
              protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {
                final Instant now = Instant.now();
                inhibitions
                    .stream()
                    .map(i -> new InhibitionDescription(i, now))
                    .sorted()
                    .forEach(
                        description -> {
                          try {
                            description.emit(writer);
                          } catch (XMLStreamException e) {
                            throw new RuntimeException(e);
                          }
                        });
              }
            }.renderPage(os);
          }
        });
    add(
        "/add",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody()) {
            new BasePage(this, false) {

              @Override
              public Stream<Header> headers() {
                try {
                  return Stream.of(
                      Header.jsModule(
                          String.format(
                              "import form from './ui.js'; form(document.getElementById('form'), %s, %s);",
                              mapper.writeValueAsString(knownEnvironments),
                              mapper.writeValueAsString(knownServices))));
                } catch (JsonProcessingException e) {
                  e.printStackTrace();
                  return Stream.of(
                      Header.jsModule(
                          "document.getElementById('form').innerText = 'Internal error';"));
                }
              }

              @Override
              protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {
                writer.writeStartElement("div");
                writer.writeAttribute("id", "form");
                writer.writeEndElement();
              }
            }.renderPage(os);
          }
        });

    add(
        "/metrics",
        t -> {
          t.getResponseHeaders().set("Content-type", TextFormat.CONTENT_TYPE_004);
          t.sendResponseHeaders(200, 0);
          try (final OutputStream os = t.getResponseBody();
              Writer writer = new PrintWriter(os)) {
            TextFormat.write004(writer, CollectorRegistry.defaultRegistry.metricFamilySamples());
          }
        });

    add(
        "/api/inhibitions",
        t -> {
          switch (t.getRequestMethod()) {
            case "POST":
              try {
                final CreateRequest query =
                    mapper.readValue(t.getRequestBody(), CreateRequest.class);
                if (query.getCreator() == null
                    || query.getCreator().isEmpty()
                    || query.getEnvironment() == null
                    || query.getEnvironment().isEmpty()
                    || query.getServices() == null
                    || query.getServices().isEmpty()) {
                  t.sendResponseHeaders(400, 0);
                  try (final OutputStream os = t.getResponseBody()) {
                    os.write("Creator and services are required".getBytes(StandardCharsets.UTF_8));
                  }
                  return;
                }
                knownServices.addAll(query.getServices());
                knownEnvironments.add(query.getEnvironment());
                final Inhibition inhibition =
                    new Inhibition(
                        new TreeSet<>(query.getServices()),
                        query.getEnvironment(),
                        Instant.now().plusSeconds(query.getTtl()),
                        query.getCreator(),
                        query.getReason());
                inhibitions.add(inhibition);
                refreshPrometheus();
                final CreateResponse response = new CreateResponse();
                response.setId(inhibition.id());
                response.setExpirationTime(inhibition.expirationTime().getEpochSecond());
                t.getResponseHeaders().set("Content-type", "application/json");
                t.sendResponseHeaders(201, 0);
                try (final OutputStream os = t.getResponseBody()) {
                  os.write(mapper.writeValueAsString(response).getBytes(StandardCharsets.UTF_8));
                }
              } catch (final Exception e) {
                e.printStackTrace();
                t.sendResponseHeaders(400, 0);
                try (final OutputStream os = t.getResponseBody()) {
                  final ObjectNode result = mapper.createObjectNode();
                  result.put("error", e.getMessage());
                  os.write(mapper.writeValueAsBytes(result));
                }
                return;
              }

              break;
            case "DELETE":
              try {
                final int id = mapper.readValue(t.getRequestBody(), int.class);
                for (final Inhibition inhibition : inhibitions) {
                  if (inhibition.id() != id) continue;
                  inhibition.wake();
                  t.sendResponseHeaders(204, -1);
                  t.getResponseBody().close();
                  refreshPrometheus();
                  return;
                }

                t.sendResponseHeaders(404, 0);
                try (final OutputStream os = t.getResponseBody()) {
                  os.write("{\"error\":\"No such inhibition\"}".getBytes(StandardCharsets.UTF_8));
                }
              } catch (final Exception e) {
                e.printStackTrace();
                t.sendResponseHeaders(400, 0);
                try (OutputStream os = t.getResponseBody()) {
                  final ObjectNode result = mapper.createObjectNode();
                  result.put("error", e.getMessage());
                  os.write(mapper.writeValueAsBytes(result));
                }
              }

              break;
            case "GET":
              try {
                ArrayNode inhibitionDump = mapper.createArrayNode();
                Instant now = Instant.now();
                for (Inhibition i : inhibitions) {
                  if (i.expirationTime().isAfter(now) && !i.awoken())
                    inhibitionDump.add(mapper.convertValue(i, ObjectNode.class));
                }
                t.getResponseHeaders().set("Content-type", "application/json");
                t.sendResponseHeaders(200, 0);
                try (final OutputStream os = t.getResponseBody()) {
                  os.write(mapper.writeValueAsBytes(inhibitionDump));
                }
              } catch (final Exception e) {
                e.printStackTrace();
                t.sendResponseHeaders(400, 0);
                try (OutputStream os = t.getResponseBody()) {
                  final ObjectNode result = mapper.createObjectNode();
                  result.put("error", e.getMessage());
                  os.write(mapper.writeValueAsBytes(result));
                }
              }
          }
        });

    add(
        "/api/load",
        t -> {
          if (t.getRequestMethod().equals("POST")) {
            try {
              ArrayList<Inhibition> inhibitionsToRecreate =
                  mapper.readValue(t.getRequestBody(), new TypeReference<List<Inhibition>>() {});
              Instant now = Instant.now();
              for (Inhibition i : inhibitionsToRecreate) {
                // Only keep Inhibitions which haven't expired during downtime
                if (i.expirationTime().isAfter(now)) {
                  inhibitions.add(i);
                  knownEnvironments.add(i.environment());
                  knownServices.addAll(i.services());
                }
              }
              refreshPrometheus();
              t.getResponseHeaders().set("Content-type", "application/json");
              t.sendResponseHeaders(201, 0);
              try (final OutputStream os = t.getResponseBody()) {
                os.write(mapper.writeValueAsString(inhibitions).getBytes(StandardCharsets.UTF_8));
              }
            } catch (final Exception e) {
              e.printStackTrace();
              t.sendResponseHeaders(400, 0);
              try (OutputStream os = t.getResponseBody()) {
                final ObjectNode result = mapper.createObjectNode();
                result.put("error", e.getMessage());
                os.write(mapper.writeValueAsBytes(result));
              }
            }
          }
        });

    add("ui.js", "text/javascript");
    add("swagger.json", "application/json");
    add("api-docs/favicon-16x16.png", "image/png");
    add("api-docs/favicon-32x32.png", "image/png");
    add("api-docs/index.html", "text/html");
    add("api-docs/oauth2-redirect.html", "text/html");
    add("api-docs/swagger-ui-bundle.js", "text/javascript");
    add("api-docs/swagger-ui-standalone-preset.js", "text/javascript");
    add("api-docs/swagger-ui.css", "text/css");
    add("api-docs/swagger-ui.js", "text/javascript");

    System.out.println("Starting server...");
    final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    executorService.scheduleWithFixedDelay(this::refreshPrometheus, 10, 10, TimeUnit.SECONDS);
  }

  /** Add a new service endpoint with Prometheus monitoring */
  private void add(String url, HttpHandler handler) {
    server.createContext(
        url,
        t -> {
          try (AutoCloseable timer = responseTime.start(url)) {
            handler.handle(t);
          } catch (final Throwable e) {
            e.printStackTrace();
            throw new IOException(e);
          }
        });
  }

  /** Add a file backed by a class resource */
  private void add(String url, String type) {
    server.createContext(
        "/" + url,
        t -> {
          t.getResponseHeaders().set("Content-type", type);
          t.sendResponseHeaders(200, 0);
          final byte[] b = new byte[1024];
          try (OutputStream output = t.getResponseBody();
              InputStream input = getClass().getResourceAsStream(url)) {
            int count;
            while ((count = input.read(b)) > 0) {
              output.write(b, 0, count);
            }
          } catch (final IOException e) {
            e.printStackTrace();
          }
        });
  }

  @Override
  public Stream<Header> headers() {
    return Stream.empty();
  }

  @Override
  public String name() {
    return "Somnus";
  }

  @Override
  public Stream<NavigationMenu> navigation() {
    return Stream.of(NavigationMenu.item("view", "View"), NavigationMenu.item("add", "Add"));
  }

  private void refreshPrometheus() {
    lastUpdate = Instant.now();
    for (final String environment : knownEnvironments)
      for (final String service : knownServices) {
        stopGauge
            .labels(service, environment)
            .set(
                inhibitions
                        .stream()
                        .anyMatch(
                            i ->
                                i.environment().equals(environment)
                                    && i.expirationTime().isAfter(lastUpdate)
                                    && !i.awoken()
                                    && i.test(service))
                    ? 1.0
                    : 0.0);
      }
  }
}
