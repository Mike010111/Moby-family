package by.moby.family.platform.security;

/** Пользователь из JWT: кто он в семье и в какой семье. */
public record CurrentUser(String memberId, String familyId, Role role, String token) {

    public boolean isParent() {
        return role == Role.PARENT;
    }
}
