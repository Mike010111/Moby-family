package by.moby.rewards.support;

import by.moby.rewards.demo.RewardsDemoData;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@ActiveProfiles({"test", "demo"})
public abstract class RewardsIntegrationTest {

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected RewardsDemoData demo;
    @Autowired
    protected MutableClock clock;

    @BeforeEach
    void freshDemo() {
        clock.reset();
        demo.reset();
    }

    protected ResultActions getAs(RequestPostProcessor user, String uri, Object... vars) throws Exception {
        return mvc.perform(get(uri, vars).with(user));
    }

    protected ResultActions postAs(RequestPostProcessor user, String uri, Object... vars) throws Exception {
        return mvc.perform(post(uri, vars).with(user));
    }

    protected ResultActions buy(RequestPostProcessor user, String childId, String artifactId) throws Exception {
        return postAs(user, "/api/rewards/children/{c}/artifacts/{a}/purchase", childId, artifactId);
    }
}
