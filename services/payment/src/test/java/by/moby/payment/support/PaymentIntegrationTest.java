package by.moby.payment.support;

import by.moby.payment.account.Account;
import by.moby.payment.account.AccountKind;
import by.moby.payment.account.AccountRepository;
import by.moby.payment.account.LedgerTransactionRepository;
import by.moby.payment.demo.PaymentDemoData;
import by.moby.payment.payments.Perks;
import by.moby.payment.payments.RewardsClient;
import by.moby.common.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Поднимает payment на Testcontainers Postgres с демо-данными; перед каждым тестом — «свежая» семья. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@ActiveProfiles({"test", "demo"})
public abstract class PaymentIntegrationTest {

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected PaymentDemoData demo;
    @Autowired
    protected MutableClock clock;
    @Autowired
    protected AccountRepository accounts;
    @Autowired
    protected LedgerTransactionRepository ledger;
    @MockitoBean
    protected RewardsClient rewards;

    @BeforeEach
    void freshDemo() {
        clock.reset();
        demo.reset();
        when(rewards.perks(any(), any(), any())).thenReturn(new Perks(2, null));
    }

    protected Money balance(String childId) {
        return accounts.findByOwnerIdAndKind(childId, AccountKind.CHILD).map(Account::getBalance).orElseThrow();
    }

    protected Money parentBalance() {
        return accounts.findByOwnerIdAndKind("mom", AccountKind.PARENT).map(Account::getBalance).orElseThrow();
    }

    /** Сверка ledger: баланс каждого клиентского счёта = Σ его проводок, Σ всех проводок = 0. */
    protected void assertLedgerReconciles() {
        for (Account a : accounts.findAll()) {
            if (a.getKind() != AccountKind.SYSTEM) {
                assertThat(ledger.balanceFromEntries(a.getId())).as("ledger of %s", a.getId()).isEqualTo(a.getBalance());
            }
        }
        assertThat(ledger.sumAllEntries()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    protected ResultActions perform(MockHttpServletRequestBuilder request, RequestPostProcessor user) throws Exception {
        return mvc.perform(request.with(user));
    }

    protected ResultActions getAs(RequestPostProcessor user, String uri, Object... vars) throws Exception {
        return perform(get(uri, vars), user);
    }

    protected ResultActions postAs(RequestPostProcessor user, String uri, String json, Object... vars) throws Exception {
        return perform(post(uri, vars).contentType(MediaType.APPLICATION_JSON).content(json)
                .header("Idempotency-Key", UUID.randomUUID().toString()), user);
    }

    protected ResultActions putAs(RequestPostProcessor user, String uri, String json, Object... vars) throws Exception {
        return perform(put(uri, vars).contentType(MediaType.APPLICATION_JSON).content(json), user);
    }

    protected ResultActions pay(RequestPostProcessor child, String childId, String merchantId) throws Exception {
        return postAs(child, "/api/payment/children/{id}/qr-payments", "{\"merchantId\":\"" + merchantId + "\"}", childId);
    }
}
