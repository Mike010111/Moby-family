package by.moby.family.scenarios;

import by.moby.family.support.FamilyIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static by.moby.family.support.TestUsers.anna;
import static by.moby.family.support.TestUsers.ivan;
import static by.moby.family.support.TestUsers.misha;
import static by.moby.family.support.TestUsers.stranger;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FamilyAccessControlTest extends FamilyIntegrationTest {

    @Test
    @DisplayName("Ребёнок видит только себя и свои запросы")
    void childSeesOwn() throws Exception {
        postAs(ivan(), "/api/family/requests", "{\"childId\":\"ivan\",\"amount\":\"5\"}");
        getAs(misha(), "/api/family/me").andExpect(jsonPath("$.children", hasSize(1))).andExpect(jsonPath("$.children[0].id").value("misha"))
                .andExpect(jsonPath("$.relatives", hasSize(3)));
        getAs(misha(), "/api/family/requests").andExpect(jsonPath("$", hasSize(0)));
        getAs(anna(), "/api/family/requests").andExpect(jsonPath("$", hasSize(1)));
        getAs(anna(), "/api/family/me").andExpect(jsonPath("$.children", hasSize(2)));
    }

    @Test
    @DisplayName("Ребёнок не действует за брата и не делает родительских операций")
    void childLimits() throws Exception {
        postAs(misha(), "/api/family/requests", "{\"childId\":\"ivan\",\"amount\":\"5\"}").andExpect(status().isForbidden());
        postAs(misha(), "/api/family/children/ivan/tasks/ta1/submit", null).andExpect(status().isForbidden());
        postAs(ivan(), "/api/family/children/ivan/tasks", "{\"title\":\"x\",\"reward\":\"100\"}").andExpect(status().isForbidden());
        postAs(ivan(), "/api/family/children/ivan/tasks/ta1/approve", null).andExpect(status().isForbidden());
        postAs(misha(), "/api/family/children/misha/geo/refresh", null).andExpect(status().isForbidden());
        String id = body(postAs(misha(), "/api/family/requests", "{\"childId\":\"misha\",\"amount\":\"5\"}")).at("/request/id").asString();
        postAs(ivan(), "/api/family/requests/{id}/approve", null, id).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        postAs(misha(), "/api/family/requests/{id}/decline", null, id).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Родитель чужой семьи ничего не может")
    void strangerParent() throws Exception {
        String id = body(postAs(misha(), "/api/family/requests", "{\"childId\":\"misha\",\"amount\":\"5\"}")).at("/request/id").asString();
        postAs(stranger(), "/api/family/requests/{id}/approve", null, id).andExpect(status().isForbidden());
        postAs(stranger(), "/api/family/children/misha/tasks", "{\"title\":\"x\",\"reward\":\"1\"}").andExpect(status().isForbidden());
        postAs(stranger(), "/api/family/children/misha/geo/refresh", null).andExpect(status().isForbidden());
        getAs(stranger(), "/api/family/me").andExpect(jsonPath("$.children", hasSize(0)));
    }

    @Test
    @DisplayName("Родитель не выполняет задания и не копит за ребёнка")
    void parentIsNotTheChild() throws Exception {
        postAs(anna(), "/api/family/children/misha/tasks/t1/submit", null).andExpect(status().isForbidden());
        postAs(anna(), "/api/family/children/misha/piggies/pg-bike/deposits", "{\"amount\":\"1\"}").andExpect(status().isForbidden());
        postAs(anna(), "/api/family/children/misha/lessons/complete", null).andExpect(status().isForbidden());
    }
}
