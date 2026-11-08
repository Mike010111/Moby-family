package by.moby.payment.support;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/** JWT демо-пользователей, как их выпускает Keycloak (claims member_id, family_id, roles). */
public final class TestUsers {

    private TestUsers() {
    }

    public static RequestPostProcessor user(String memberId, String familyId, String role) {
        return jwt()
                .jwt(j -> j.subject(memberId)
                        .claim("member_id", memberId)
                        .claim("family_id", familyId)
                        .claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    public static RequestPostProcessor anna() {
        return user("mom", "fam-demo", "PARENT");
    }

    public static RequestPostProcessor misha() {
        return user("misha", "fam-demo", "CHILD_JUNIOR");
    }

    public static RequestPostProcessor ivan() {
        return user("ivan", "fam-demo", "CHILD_TEEN");
    }

    /** Родитель другой семьи. */
    public static RequestPostProcessor stranger() {
        return user("other-parent", "fam-other", "PARENT");
    }
}
