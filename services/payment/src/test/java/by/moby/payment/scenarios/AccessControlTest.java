package by.moby.payment.scenarios;

import by.moby.payment.support.PaymentIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static by.moby.payment.support.TestUsers.anna;
import static by.moby.payment.support.TestUsers.ivan;
import static by.moby.payment.support.TestUsers.misha;
import static by.moby.payment.support.TestUsers.stranger;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Требование №11: родитель — только дети своей семьи, ребёнок — только своё. */
class AccessControlTest extends PaymentIntegrationTest {

    @Test
    @DisplayName("Без токена — 401")
    void anonymous() throws Exception {
        mvc.perform(get("/api/payment/children/misha/account")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Ребёнок не видит счёт брата")
    void childSeesOnlyOwn() throws Exception {
        getAs(misha(), "/api/payment/children/misha/account").andExpect(status().isOk());
        getAs(ivan(), "/api/payment/children/misha/account").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        getAs(misha(), "/api/payment/children/ivan/operations").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Родитель чужой семьи не управляет ребёнком")
    void parentOfOtherFamily() throws Exception {
        getAs(stranger(), "/api/payment/children/misha/account").andExpect(status().isForbidden());
        putAs(stranger(), "/api/payment/children/misha/frozen", "{\"frozen\":true}").andExpect(status().isForbidden());
        postAs(stranger(), "/api/payment/children/misha/topups", "{\"amount\":\"5\"}").andExpect(status().isForbidden());
        getAs(stranger(), "/api/payment/me/parent-account").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Ребёнок не может делать родительские операции")
    void childCannotActAsParent() throws Exception {
        putAs(misha(), "/api/payment/children/misha/frozen", "{\"frozen\":false}").andExpect(status().isForbidden());
        putAs(ivan(), "/api/payment/children/ivan/categories/games", "{\"allowed\":true}").andExpect(status().isForbidden());
        postAs(ivan(), "/api/payment/children/ivan/limits", "{\"count\":1,\"unit\":\"day\",\"amount\":\"100\",\"categories\":[]}")
                .andExpect(status().isForbidden());
        postAs(ivan(), "/api/payment/children/ivan/topups", "{\"amount\":\"100\"}").andExpect(status().isForbidden());
        postAs(ivan(), "/api/payment/internal/children/ivan/p2p-approvals", "{\"requestId\":\"r\",\"max\":100}").andExpect(status().isForbidden());
        getAs(misha(), "/api/payment/me/parent-account").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Платит и переводит только сам ребёнок, не родитель и не брат")
    void onlyTheChildPays() throws Exception {
        pay(anna(), "misha", "m-buffet").andExpect(status().isForbidden());
        pay(ivan(), "misha", "m-buffet").andExpect(status().isForbidden());
        postAs(anna(), "/api/payment/children/ivan/p2p-transfers", "{\"contactId\":\"c-katya\",\"amount\":\"1\"}").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Родитель видит обоих детей своей семьи")
    void parentSeesOwnChildren() throws Exception {
        getAs(anna(), "/api/payment/children/misha/account").andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(12.4));
        getAs(anna(), "/api/payment/children/ivan/account").andExpect(status().isOk()).andExpect(jsonPath("$.stats.txCount").value(1243));
        getAs(anna(), "/api/payment/me/parent-account").andExpect(jsonPath("$.balance").value(842.6))
                .andExpect(jsonPath("$.cardMask").value("•• 7710"));
    }
}
