package by.moby.family.requests;

import by.moby.common.events.RequestKind;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChildRequestRepository extends JpaRepository<ChildRequest, String> {

    /** Одобрение и отклонение сериализуются блокировкой строки запроса. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ChildRequest r where r.id = :id")
    Optional<ChildRequest> lockById(@Param("id") String id);

    List<ChildRequest> findByFamilyIdOrderByCreatedAtDesc(String familyId);

    List<ChildRequest> findByChildIdOrderByCreatedAtDesc(String childId);

    List<ChildRequest> findByChildIdAndKindAndStatus(String childId, RequestKind kind, RequestStatus status);

    boolean existsByPiggyIdAndStatus(String piggyId, RequestStatus status);
}
