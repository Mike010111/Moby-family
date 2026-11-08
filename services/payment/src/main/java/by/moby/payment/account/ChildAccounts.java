package by.moby.payment.account;

import by.moby.common.error.BusinessException;
import by.moby.common.error.ErrorCode;
import by.moby.payment.platform.security.AccessPolicy;
import by.moby.payment.platform.security.CurrentUser;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Поиск счетов с проверкой прав и блокировкой. */
@Component
public class ChildAccounts {

    private final AccountRepository accounts;
    private final AccessPolicy access;
    private final EntityManager entityManager;

    public ChildAccounts(AccountRepository accounts, AccessPolicy access, EntityManager entityManager) {
        this.accounts = accounts;
        this.access = access;
        this.entityManager = entityManager;
    }

    public Account child(String childId) {
        return accounts.findByOwnerIdAndKind(childId, AccountKind.CHILD)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Счёт ребёнка не найден"));
    }

    public Account parentOfFamily(String familyId) {
        return accounts.findByFamilyIdAndKind(familyId, AccountKind.PARENT)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Счёт родителя не найден"));
    }

    public Account savings(String piggyId) {
        return accounts.findByExternalRefAndKind(piggyId, AccountKind.SAVINGS)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Счёт копилки не найден"));
    }

    public Account system(String id) {
        return accounts.findById(id).orElseThrow(() -> new IllegalStateException("System account missing: " + id));
    }

    /** Читать счёт ребёнка: сам ребёнок или родитель его семьи. */
    public Account readableChild(String childId) {
        Account account = child(childId);
        access.requireChildOrParent(account.getFamilyId(), childId);
        return account;
    }

    /** Счёт ребёнка, которым управляет родитель этой семьи. */
    public Account managedChild(String childId) {
        Account account = child(childId);
        access.requireParentOf(account.getFamilyId());
        return account;
    }

    public CurrentUser currentUser() {
        return access.current();
    }

    /**
     * Блокирует счета {@code SELECT … FOR UPDATE} и только после этого перечитывает их состояние.
     * Счета блокируются в порядке id — одинаковый порядок во всех операциях исключает взаимные
     * блокировки. Возвращает управляемые экземпляры в порядке аргументов: проверки баланса и
     * лимитов делаются только по ним.
     *
     * <p>Блокировка явная, а не через {@code refresh(entity, PESSIMISTIC_WRITE)}: для сущности с
     * коллекцией Hibernate читает состояние до захвата блокировки (follow-on locking), и
     * параллельная транзакция получила бы устаревший баланс.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Account> lock(Account... toLock) {
        List<String> ids = Stream.of(toLock).map(Account::getId).distinct().sorted().toList();
        entityManager.createNativeQuery("select id from accounts where id in (:ids) order by id for update")
                .setParameter("ids", ids)
                .getResultList();
        Map<String, Account> locked = new HashMap<>();
        for (String id : ids) {
            Account managed = entityManager.find(Account.class, id);
            entityManager.refresh(managed);
            locked.put(id, managed);
        }
        return Stream.of(toLock).map(a -> locked.get(a.getId())).toList();
    }

    public Account lock(Account account) {
        return lock(new Account[]{account}).getFirst();
    }
}
