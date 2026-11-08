package by.moby.family.payment;

import by.moby.common.error.BusinessException;
import by.moby.common.error.ErrorCode;
import by.moby.common.money.Money;
import by.moby.family.platform.security.CurrentUser;
import by.moby.family.platform.security.CurrentUserProvider;
import by.moby.family.platform.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Контракт family → payment: ретрансляция JWT, Idempotency-Key, перевод ProblemDetail в BusinessException. */
class HttpPaymentClientTest {

    MockRestServiceServer server;
    HttpPaymentClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://payment");
        server = MockRestServiceServer.bindTo(builder).build();
        CurrentUserProvider users = mock(CurrentUserProvider.class);
        when(users.get()).thenReturn(new CurrentUser("mom", "fam-demo", Role.PARENT, "jwt-token"));
        client = new HttpPaymentClient(builder.build(), users);
    }

    @Test
    void relaysTokenAndIdempotencyKey() {
        server.expect(requestTo("http://payment/api/payment/internal/transfers/parent-to-child"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer jwt-token"))
                .andExpect(header("Idempotency-Key", "request:1"))
                .andRespond(withSuccess("{\"transactionId\":\"t\",\"amount\":5.00,\"childBalance\":17.40,\"counterpartBalance\":837.60}",
                        MediaType.APPLICATION_JSON));
        var result = client.parentToChild("misha", Money.of("5"), PaymentClient.TransferKind.TOPUP, "Мама одобрила: На обед", "request:1");
        assertThat(result.childBalance()).isEqualTo(Money.of("17.40"));
        server.verify();
    }

    @Test
    void translatesProblemDetail() {
        server.expect(requestTo("http://payment/api/payment/internal/transfers/parent-to-child"))
                .andRespond(withStatus(HttpStatus.LOCKED).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"status\":423,\"detail\":\"Счёт заблокирован\",\"code\":\"FROZEN\"}"));
        assertThatThrownBy(() -> client.parentToChild("misha", Money.of("5"), PaymentClient.TransferKind.TOPUP, "x", "k"))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.FROZEN);
                    assertThat(e.reason()).isEqualTo("Счёт заблокирован");
                });
    }

    @Test
    void serverErrorsBecomeUnavailable() {
        server.expect(requestTo("http://payment/api/payment/internal/savings/pg-1/withdraw"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThatThrownBy(() -> client.withdrawSavings("pg-1", "x", "k"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UNAVAILABLE));
    }
}
