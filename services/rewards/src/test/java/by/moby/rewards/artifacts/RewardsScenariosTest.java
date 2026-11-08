package by.moby.rewards.artifacts;

import by.moby.rewards.support.RewardsIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static by.moby.rewards.support.TestUsers.anna;
import static by.moby.rewards.support.TestUsers.ivan;
import static by.moby.rewards.support.TestUsers.misha;
import static by.moby.rewards.support.TestUsers.stranger;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** JUnit-аналоги сценариев магазина артефактов из store.test.js. */
class RewardsScenariosTest extends RewardsIntegrationTest {

    @Test
    @DisplayName("Неделя «в руках»: покупка за баллы ребёнка, белка и кэшбэк меняются")
    void buyForChildPoints() throws Exception {
        getAs(misha(), "/api/rewards/children/misha").andExpect(jsonPath("$.weekSlot").value("hands"))
                .andExpect(jsonPath("$.owned.hands").doesNotExist());
        buy(misha(), "misha", "diamond").andExpect(status().isOk()).andExpect(jsonPath("$.points").value(120))
                .andExpect(jsonPath("$.artifact.cashback").value(5));
        getAs(misha(), "/api/rewards/children/misha").andExpect(jsonPath("$.owned.hands.id").value("diamond"));
        getAs(misha(), "/api/rewards/children/misha/payment-perks?category=cafe&amount=2.40")
                .andExpect(jsonPath("$.cashback.percent").value(5)).andExpect(jsonPath("$.points").value(2));
        getAs(anna(), "/api/rewards/me/points").andExpect(jsonPath("$.points").value(3420));
    }

    @Test
    @DisplayName("Повторная покупка за неделю заменяет артефакт; купленный — недоступен до конца недели")
    void secondPurchaseReplaces() throws Exception {
        buy(misha(), "misha", "nut");
        buy(misha(), "misha", "coin").andExpect(jsonPath("$.replaced").value("Лесной орех"));
        getAs(misha(), "/api/rewards/children/misha")
                .andExpect(jsonPath("$.shop[?(@.id=='nut')].status").value("soldout"))
                .andExpect(jsonPath("$.shop[?(@.id=='coin')].status").value("equipped"));
        buy(misha(), "misha", "nut").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SOLDOUT"));
        buy(misha(), "misha", "coin").andExpect(jsonPath("$.code").value("EQUIPPED"));
        getAs(misha(), "/api/rewards/children/misha/payment-perks?category=food&amount=3").andExpect(jsonPath("$.cashback").doesNotExist());
        getAs(misha(), "/api/rewards/children/misha/payment-perks?category=books&amount=3").andExpect(jsonPath("$.cashback.percent").value(4));
    }

    @Test
    @DisplayName("Не хватает баллов — покупки нет")
    void notEnoughPoints() throws Exception {
        buy(misha(), "misha", "diamond");
        buy(misha(), "misha", "coin").andExpect(status().isOk()).andExpect(jsonPath("$.points").value(20));
        buy(misha(), "misha", "gamepad").andExpect(jsonPath("$.code").value("POINTS")).andExpect(jsonPath("$.detail").value("Не хватает 80 баллов"));
        buy(misha(), "misha", "glasses-dark").andExpect(jsonPath("$.code").value("NOT_FOR_SALE"));
        postAs(misha(), "/api/rewards/children/misha/artifacts/renew").andExpect(jsonPath("$.code").value("NOTHING_TO_RENEW"));
    }

    @Test
    @DisplayName("Слоты: у 7–13 четыре, у 14–17 пять (с обувью)")
    void slots() throws Exception {
        getAs(misha(), "/api/rewards/children/misha").andExpect(jsonPath("$.cycle", contains("hat", "glasses", "outer", "hands")))
                .andExpect(jsonPath("$.owned.glasses.id").value("glasses-blue"));
        getAs(ivan(), "/api/rewards/children/ivan").andExpect(jsonPath("$.cycle", contains("hat", "glasses", "outer", "hands", "shoes")))
                .andExpect(jsonPath("$.owned.outer.id").value("vest-brown"));
        buy(ivan(), "ivan", "nut").andExpect(jsonPath("$.artifact.category").value("food"));
        buy(ivan(), "ivan", "coin").andExpect(jsonPath("$.artifact.category").value("transport"));
    }

    @Test
    @DisplayName("Покупает только сам ребёнок; родитель видит магазин детей своей семьи")
    void access() throws Exception {
        buy(ivan(), "misha", "nut").andExpect(status().isForbidden());
        buy(anna(), "misha", "nut").andExpect(status().isForbidden());
        getAs(anna(), "/api/rewards/children/misha").andExpect(status().isOk());
        getAs(stranger(), "/api/rewards/children/misha").andExpect(status().isForbidden());
        getAs(ivan(), "/api/rewards/children/misha/payment-perks?category=cafe&amount=1").andExpect(status().isForbidden());
    }
}
