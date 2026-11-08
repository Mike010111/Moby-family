package by.moby.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

/** Gateway не пропускает запросы без JWT и проксирует аутентифицированные, сохраняя заголовок Authorization. */
@SpringBootTest
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class GatewayRoutingTest {

    static final AtomicReference<String> lastPath = new AtomicReference<>();
    static final HttpServer backend = startBackend();

    static HttpServer startBackend() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                lastPath.set(exchange.getRequestURI().getPath());
                byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void stop() {
        backend.stop(0);
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + backend.getAddress().getPort();
        registry.add("PAYMENT_URL", () -> url);
        registry.add("FAMILY_URL", () -> url);
        registry.add("REWARDS_URL", () -> url);
        registry.add("NOTIFICATION_URL", () -> url);
    }

    @Autowired
    WebTestClient web;

    @Test
    @DisplayName("Без токена — 401, до сервиса запрос не доходит")
    void rejectsAnonymous() {
        lastPath.set(null);
        web.get().uri("/api/payment/children/misha/account").exchange().expectStatus().isUnauthorized();
        assertThat(lastPath.get()).isNull();
    }

    @Test
    @DisplayName("С токеном — маршрут по префиксу сервиса")
    void routesAuthenticated() {
        for (String path : new String[]{"/api/payment/children/misha/account", "/api/family/me", "/api/rewards/me/points", "/api/notifications/feed"}) {
            web.mutateWith(mockJwt().jwt(j -> j.claim("member_id", "mom")))
                    .get().uri(path).exchange().expectStatus().isOk();
            assertThat(lastPath.get()).isEqualTo(path);
        }
    }

    @Test
    @DisplayName("Неизвестный путь закрыт, health открыт")
    void unknownPathsDenied() {
        web.mutateWith(mockJwt()).get().uri("/internal/anything").exchange().expectStatus().isForbidden();
        web.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }
}
