package by.moby.family.scenarios;

import by.moby.common.events.LessonCompleted;
import by.moby.common.events.PiggyCompleted;
import by.moby.common.events.PiggyDeposited;
import by.moby.common.events.RequestCreated;
import by.moby.common.events.RequestKind;
import by.moby.common.events.RequestResolved;
import by.moby.common.events.TaskApproved;
import by.moby.common.events.TaskSubmitted;
import by.moby.common.money.Money;
import by.moby.family.members.FrozenProjection;
import by.moby.family.support.FamilyIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import static by.moby.family.support.TestUsers.anna;
import static by.moby.family.support.TestUsers.ivan;
import static by.moby.family.support.TestUsers.misha;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** JUnit-аналоги сценариев tests/store.test.js про запросы, задания, копилки и уроки. */
class FamilyScenariosTest extends FamilyIntegrationTest {

    @Autowired
    FrozenProjection frozen;

    @Test
    @DisplayName("Запрос «5 BYN на обед» главному родственнику → одобрение меняет оба баланса и уведомляет ребёнка")
    void lunchRequestApproved() throws Exception {
        Money p0 = payment.parentBalance();
        Money k0 = payment.balance("misha");
        JsonNode created = body(postAs(misha(), "/api/family/requests", """
                {"childId":"misha","relativeId":"mom","amount":"5","reason":"На обед"}""").andExpect(status().isCreated()));
        String id = created.at("/request/id").asString();
        assertThat(created.at("/request/status").asString()).isEqualTo("PENDING");
        assertThat(events.stream(RequestCreated.class)).singleElement()
                .satisfies(e -> assertThat(e.toMain()).isTrue())
                .satisfies(e -> assertThat(e.requestId()).isEqualTo(id));

        postAs(anna(), "/api/family/requests/{id}/approve", null, id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        assertThat(payment.parentBalance()).isEqualTo(p0.minus(Money.of("5")));
        assertThat(payment.balance("misha")).isEqualTo(k0.plus(Money.of("5")));
        assertThat(payment.transfers()).singleElement().satisfies(t -> {
            assertThat(t.title()).isEqualTo("Мама одобрила: На обед");
            assertThat(t.key()).isEqualTo("request:" + id);
        });
        assertThat(events.stream(RequestResolved.class)).singleElement().satisfies(e -> {
            assertThat(e.approved()).isTrue();
            assertThat(e.resolver().label()).isEqualTo("Мама");
            assertThat(e.resolver().female()).isTrue();
        });
        postAs(anna(), "/api/family/requests/{id}/approve", null, id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_PROCESSED"));
    }

    @Test
    @DisplayName("Запрос не главному родственнику уходит, но в хаб не попадает")
    void requestToOtherRelative() throws Exception {
        postAs(misha(), "/api/family/requests", """
                {"childId":"misha","relativeId":"dad","amount":"3","reason":"На проезд"}""")
                .andExpect(jsonPath("$.request.status").value("SENT"))
                .andExpect(jsonPath("$.relativeDative").value("папе"));
        assertThat(events.stream(RequestCreated.class)).singleElement().satisfies(e -> assertThat(e.toMain()).isFalse());
        postAs(misha(), "/api/family/requests", "{\"childId\":\"misha\",\"amount\":\"501\"}").andExpect(jsonPath("$.code").value("TOO_LARGE"));
        postAs(misha(), "/api/family/requests", "{\"childId\":\"misha\",\"amount\":\"1.234\"}").andExpect(jsonPath("$.code").value("AMOUNT"));
    }

    @Test
    @DisplayName("Блокировка счёта: операции стоят, GPS и уроки работают")
    void frozenAccount() throws Exception {
        frozen.apply("misha", true);
        postAs(misha(), "/api/family/children/misha/piggies/pg-bike/deposits", "{\"amount\":\"1\"}").andExpect(jsonPath("$.code").value("FROZEN"));
        postAs(misha(), "/api/family/children/misha/tasks/t1/submit", null).andExpect(jsonPath("$.code").value("FROZEN"));
        postAs(anna(), "/api/family/children/misha/tasks", "{\"title\":\"Тест\",\"reward\":\"1\"}").andExpect(jsonPath("$.code").value("FROZEN"));
        postAs(misha(), "/api/family/requests", "{\"childId\":\"misha\",\"relativeId\":\"mom\",\"amount\":\"5\"}")
                .andExpect(status().isLocked()).andExpect(jsonPath("$.code").value("FROZEN"));
        postAs(anna(), "/api/family/children/misha/geo/refresh", null).andExpect(status().isOk());
        postAs(misha(), "/api/family/children/misha/lessons/complete", null).andExpect(status().isOk()).andExpect(jsonPath("$.done").value(4));
        getAs(anna(), "/api/family/me").andExpect(jsonPath("$.children[0].frozen").value(true));
    }

    @Test
    @DisplayName("Запрос, отправленный до блокировки, нельзя одобрить, пока счёт заблокирован")
    void approveAfterFreeze() throws Exception {
        String id = body(postAs(misha(), "/api/family/requests", "{\"childId\":\"misha\",\"relativeId\":\"mom\",\"amount\":\"5\",\"reason\":\"На обед\"}"))
                .at("/request/id").asString();
        frozen.apply("misha", true);
        postAs(anna(), "/api/family/requests/{id}/approve", null, id)
                .andExpect(jsonPath("$.code").value("FROZEN"))
                .andExpect(jsonPath("$.detail").value("Счёт Миша заблокирован — сначала разблокируйте"));
    }

    @Test
    @DisplayName("Пока проекция не обновилась, заморозку всё равно проверяет payment")
    void paymentIsTheLastLineOfDefence() throws Exception {
        String id = body(postAs(misha(), "/api/family/requests", "{\"childId\":\"misha\",\"amount\":\"5\"}")).at("/request/id").asString();
        payment.freeze("misha");
        postAs(anna(), "/api/family/requests/{id}/approve", null, id).andExpect(jsonPath("$.code").value("FROZEN"));
        getAs(anna(), "/api/family/requests").andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("P2P сверх лимита: запрос → одобрение → разовое одобрение в payment")
    void p2pOverRequest() throws Exception {
        postAs(ivan(), "/api/family/requests/p2p-over", "{\"childId\":\"ivan\",\"contactId\":\"c-katya\",\"amount\":\"999\"}")
                .andExpect(jsonPath("$.code").value("FUNDS"));
        postAs(ivan(), "/api/family/requests/p2p-over", "{\"childId\":\"ivan\",\"contactId\":\"c-katya\",\"amount\":\"10\"}")
                .andExpect(jsonPath("$.code").value("NOT_NEEDED"));
        postAs(ivan(), "/api/family/requests/p2p-over", "{\"childId\":\"ivan\",\"contactId\":\"nobody\",\"amount\":\"35\"}")
                .andExpect(jsonPath("$.code").value("VALIDATION"));
        String first = body(postAs(ivan(), "/api/family/requests/p2p-over", "{\"childId\":\"ivan\",\"contactId\":\"c-katya\",\"amount\":\"34\"}"))
                .at("/request/id").asString();
        String id = body(postAs(ivan(), "/api/family/requests/p2p-over", "{\"childId\":\"ivan\",\"contactId\":\"c-katya\",\"amount\":\"35\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.request.reason").value("Перевод: Катя Соколова"))).at("/request/id").asString();
        getAs(anna(), "/api/family/requests").andExpect(jsonPath("$[?(@.id=='" + first + "')].status").value("CANCELLED"));
        assertThat(events.stream(RequestCreated.class)).last().satisfies(e -> assertThat(e.limitRemaining()).isEqualTo(Money.of("24.50")));
        postAs(anna(), "/api/family/requests/{id}/approve", null, id).andExpect(status().isOk());
        assertThat(payment.approval("ivan")).isEqualTo(Money.of("35"));
        postAs(misha(), "/api/family/requests/p2p-over", "{\"childId\":\"misha\",\"contactId\":\"c-katya\",\"amount\":\"35\"}")
                .andExpect(jsonPath("$.code").value("AGE"));
    }

    @Test
    @DisplayName("Баллы ребёнка изолированы, задание приносит награду")
    void taskRewardAndPoints() throws Exception {
        postAs(misha(), "/api/family/children/misha/lessons/complete", null).andExpect(jsonPath("$.done").value(4));
        assertThat(events.stream(LessonCompleted.class)).singleElement().satisfies(e -> assertThat(e.points()).isEqualTo(10));
        postAs(misha(), "/api/family/children/misha/tasks/t1/submit", null).andExpect(jsonPath("$.status").value("REVIEW"));
        assertThat(events.stream(TaskSubmitted.class)).singleElement().satisfies(e -> assertThat(e.reward()).isEqualTo(Money.of("2")));
        Money b = payment.balance("misha");
        Money p = payment.parentBalance();
        postAs(anna(), "/api/family/children/misha/tasks/t1/approve", null).andExpect(jsonPath("$.status").value("DONE"));
        assertThat(payment.balance("misha")).isEqualTo(b.plus(Money.of("2")));
        assertThat(payment.parentBalance()).isEqualTo(p.minus(Money.of("2")));
        assertThat(payment.transfers()).singleElement().satisfies(t -> assertThat(t.title()).isEqualTo("Награда: Прочитать 20 страниц книги"));
        assertThat(events.stream(TaskApproved.class)).singleElement().satisfies(e -> assertThat(e.points()).isEqualTo(5));
        postAs(anna(), "/api/family/children/misha/tasks/t1/approve", null).andExpect(jsonPath("$.code").value("ALREADY_PROCESSED"));
        postAs(anna(), "/api/family/children/misha/tasks/t2/approve", null)
                .andExpect(jsonPath("$.code").value("ALREADY_PROCESSED")).andExpect(jsonPath("$.detail").value("Задание уже проверено"));
    }

    @Test
    @DisplayName("Родитель добавляет задание; награда 0 допустима")
    void addTask() throws Exception {
        postAs(anna(), "/api/family/children/ivan/tasks", "{\"title\":\"  Вынести мусор  \",\"reward\":\"0\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("Вынести мусор")).andExpect(jsonPath("$.reward").value(0));
        postAs(anna(), "/api/family/children/ivan/tasks", "{\"title\":\" \",\"reward\":\"1\"}").andExpect(jsonPath("$.detail").value("Опишите задание"));
        postAs(anna(), "/api/family/children/ivan/tasks", "{\"title\":\"x\",\"reward\":\"-1\"}").andExpect(jsonPath("$.code").value("AMOUNT"));
        getAs(ivan(), "/api/family/me").andExpect(jsonPath("$.children[0].tasks[0].title").value("Вынести мусор"));
    }

    @Test
    @DisplayName("Копилка подростка")
    void teenPiggyDeposit() throws Exception {
        postAs(ivan(), "/api/family/children/ivan/piggies/pg-laptop/deposits", "{\"amount\":\"10,50\"}")
                .andExpect(jsonPath("$.piggy.saved").value(320.5));
        assertThat(events.stream(PiggyDeposited.class)).singleElement().satisfies(e -> assertThat(e.points()).isEqualTo(2));
    }

    @Test
    @DisplayName("Несколько копилок: создание, пополнение, сборка → деньги на счёт")
    void severalPiggies() throws Exception {
        String id = body(postAs(misha(), "/api/family/children/misha/piggies", "{\"goal\":\"Конструктор\",\"target\":\"8\"}")
                .andExpect(status().isCreated())).at("/id").asString();
        postAs(misha(), "/api/family/children/misha/piggies", "{\"goal\":\"конструктор\",\"target\":\"5\"}").andExpect(jsonPath("$.code").value("DUPLICATE"));
        Money b0 = payment.balance("misha");
        postAs(misha(), "/api/family/children/misha/piggies/{id}/deposits", "{\"amount\":\"9\"}", id).andExpect(jsonPath("$.code").value("ROOM"));
        postAs(misha(), "/api/family/children/misha/piggies/{id}/deposits", "{\"amount\":\"4\"}", id)
                .andExpect(jsonPath("$.completed").doesNotExist());
        postAs(misha(), "/api/family/children/misha/piggies/{id}/deposits", "{\"amount\":\"4\"}", id)
                .andExpect(jsonPath("$.completed.goal").value("Конструктор"))
                .andExpect(jsonPath("$.completed.amount").value(8));
        assertThat(payment.balance("misha")).as("собранные деньги вернулись на счёт").isEqualTo(b0);
        getAs(misha(), "/api/family/me").andExpect(jsonPath("$.children[0].piggies", hasSize(1)));
        assertThat(events.stream(PiggyCompleted.class)).singleElement().satisfies(e -> assertThat(e.amount()).isEqualTo(Money.of("8")));
    }

    @Test
    @DisplayName("Не больше пяти копилок")
    void piggyLimit() throws Exception {
        for (int i = 1; i <= 4; i++) {
            postAs(misha(), "/api/family/children/misha/piggies", "{\"goal\":\"Цель " + i + "\",\"target\":\"5\"}").andExpect(status().isCreated());
        }
        postAs(misha(), "/api/family/children/misha/piggies", "{\"goal\":\"Шестая\",\"target\":\"5\"}").andExpect(jsonPath("$.code").value("PIGGY_MAX"));
    }

    @Test
    @DisplayName("7–13: изменение и удаление копилки — только после одобрения главного родственника")
    void juniorPiggyChangesNeedApproval() throws Exception {
        putAs(misha(), "/api/family/children/misha/piggies/pg-bike/target", "{\"target\":\"300\"}")
                .andExpect(jsonPath("$.pending").value(true)).andExpect(jsonPath("$.piggy.target").value(250));
        deleteAs(misha(), "/api/family/children/misha/piggies/pg-bike").andExpect(jsonPath("$.code").value("PENDING"));
        String update = pendingRequest(RequestKind.PIGGY_UPDATE);
        postAs(anna(), "/api/family/requests/{id}/approve", null, update).andExpect(status().isOk());
        getAs(misha(), "/api/family/me").andExpect(jsonPath("$.children[0].piggies[0].target").value(300));

        putAs(misha(), "/api/family/children/misha/piggies/pg-bike/target", "{\"target\":\"50\"}").andExpect(jsonPath("$.code").value("BELOW_SAVED"));
        Money b0 = payment.balance("misha");
        deleteAs(misha(), "/api/family/children/misha/piggies/pg-bike").andExpect(jsonPath("$.pending").value(true));
        postAs(anna(), "/api/family/requests/{id}/decline", null, pendingRequest(RequestKind.PIGGY_DELETE)).andExpect(status().isOk());
        getAs(misha(), "/api/family/me").andExpect(jsonPath("$.children[0].piggies", hasSize(1)));
        deleteAs(misha(), "/api/family/children/misha/piggies/pg-bike");
        postAs(anna(), "/api/family/requests/{id}/approve", null, pendingRequest(RequestKind.PIGGY_DELETE)).andExpect(status().isOk());
        getAs(misha(), "/api/family/me").andExpect(jsonPath("$.children[0].piggies", hasSize(0)));
        assertThat(payment.balance("misha")).isEqualTo(b0.plus(Money.of("86.50")));
        assertThat(events.stream(RequestResolved.class)).last().satisfies(e -> {
            assertThat(e.returned()).isEqualTo(Money.of("86.50"));
            assertThat(e.piggyGoal()).isEqualTo("Велосипед");
        });
    }

    @Test
    @DisplayName("14–17: изменение и удаление копилки — сразу, без запросов")
    void teenPiggyChangesImmediate() throws Exception {
        putAs(ivan(), "/api/family/children/ivan/piggies/pg-laptop/target", "{\"target\":\"1500\"}").andExpect(jsonPath("$.piggy.target").value(1500));
        putAs(ivan(), "/api/family/children/ivan/piggies/pg-concert/target", "{\"target\":\"52\"}").andExpect(jsonPath("$.completed.amount").value(52));
        Money b0 = payment.balance("ivan");
        deleteAs(ivan(), "/api/family/children/ivan/piggies/pg-laptop").andExpect(jsonPath("$.returned").value(310));
        assertThat(payment.balance("ivan")).isEqualTo(b0.plus(Money.of("310")));
        getAs(anna(), "/api/family/requests").andExpect(jsonPath("$", hasSize(0)));
        putAs(ivan(), "/api/family/children/ivan/piggies/pg-laptop/target", "{\"target\":\"5\"}").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Одобрение запроса по копилке, которой уже нет, отменяет запрос")
    void approveForVanishedPiggy() throws Exception {
        deleteAs(misha(), "/api/family/children/misha/piggies/pg-bike");
        String del = pendingRequest(RequestKind.PIGGY_DELETE);
        postAs(anna(), "/api/family/requests/{id}/approve", null, del).andExpect(status().isOk());
        putAs(misha(), "/api/family/children/misha/piggies/pg-bike/target", "{\"target\":\"300\"}").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Уроки: восемь, дальше — LESSONS_DONE")
    void lessonsEnd() throws Exception {
        postAs(ivan(), "/api/family/children/ivan/lessons/complete", null).andExpect(jsonPath("$.done").value(8));
        postAs(ivan(), "/api/family/children/ivan/lessons/complete", null).andExpect(jsonPath("$.code").value("LESSONS_DONE"));
    }

    @Test
    @DisplayName("GPS: родитель обновляет точку, батарея садится, но не ниже 5%")
    void geoRefresh() throws Exception {
        String place = body(getAs(anna(), "/api/family/me")).at("/children/0/geo/place").asString();
        postAs(anna(), "/api/family/children/misha/geo/refresh", null).andExpect(status().isOk());
        JsonNode child = body(getAs(anna(), "/api/family/me")).at("/children/0");
        assertThat(child.at("/geo/place").asString()).isNotEqualTo(place);
        assertThat(child.at("/battery").asInt()).isBetween(5, 63);
    }

    private String pendingRequest(RequestKind kind) throws Exception {
        for (JsonNode r : body(getAs(anna(), "/api/family/requests"))) {
            if (r.at("/kind").asString().equals(kind.name()) && r.at("/status").asString().equals("PENDING")) {
                return r.at("/id").asString();
            }
        }
        throw new AssertionError("no pending " + kind);
    }
}
