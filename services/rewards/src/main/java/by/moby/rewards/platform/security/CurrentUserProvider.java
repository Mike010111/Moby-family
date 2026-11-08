package by.moby.rewards.platform.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {

    public static final String MEMBER_CLAIM = "member_id";
    public static final String FAMILY_CLAIM = "family_id";
    public static final String ROLES_CLAIM = "roles";

    public CurrentUser get() {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken auth)) {
            throw new AuthenticationCredentialsNotFoundException("JWT required");
        }
        var jwt = auth.getToken();
        Role role = jwt.getClaimAsStringList(ROLES_CLAIM).stream()
                .filter(r -> r.equals("PARENT") || r.startsWith("CHILD_"))
                .map(Role::valueOf)
                .findFirst()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("No Moby role in token"));
        return new CurrentUser(jwt.getClaimAsString(MEMBER_CLAIM), jwt.getClaimAsString(FAMILY_CLAIM), role, jwt.getTokenValue());
    }
}
