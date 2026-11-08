package by.moby.family.support;

import by.moby.family.demo.FamilyDemoData;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import(TestcontainersConfig.class)
@ActiveProfiles({"test", "demo"})
public abstract class FamilyIntegrationTest {

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected FamilyDemoData demo;
    @Autowired
    protected MutableClock clock;
    @Autowired
    protected FakePaymentClient payment;
    @Autowired
    protected ApplicationEvents events;
    @Autowired
    protected JsonMapper json;

    @BeforeEach
    void freshDemo() {
        clock.reset();
        payment.reset();
        demo.reset();
    }

    protected ResultActions getAs(RequestPostProcessor user, String uri, Object... vars) throws Exception {
        return mvc.perform(get(uri, vars).with(user));
    }

    protected ResultActions postAs(RequestPostProcessor user, String uri, String body, Object... vars) throws Exception {
        return mvc.perform(post(uri, vars).with(user).contentType(MediaType.APPLICATION_JSON).content(body == null ? "{}" : body)
                .header("Idempotency-Key", UUID.randomUUID().toString()));
    }

    protected ResultActions putAs(RequestPostProcessor user, String uri, String body, Object... vars) throws Exception {
        return mvc.perform(put(uri, vars).with(user).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions deleteAs(RequestPostProcessor user, String uri, Object... vars) throws Exception {
        return mvc.perform(delete(uri, vars).with(user));
    }

    protected JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
