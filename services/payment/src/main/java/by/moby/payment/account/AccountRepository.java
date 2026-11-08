package by.moby.payment.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, String> {

    Optional<Account> findByOwnerIdAndKind(String ownerId, AccountKind kind);

    Optional<Account> findByFamilyIdAndKind(String familyId, AccountKind kind);

    Optional<Account> findByExternalRefAndKind(String externalRef, AccountKind kind);

    List<Account> findByKind(AccountKind kind);
}
