package by.moby.family.piggies;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PiggyRepository extends JpaRepository<Piggy, String> {

    List<Piggy> findByChildIdAndStatusOrderByCreatedAtAsc(String childId, PiggyStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Piggy p where p.id = :id and p.status = by.moby.family.piggies.PiggyStatus.ACTIVE")
    Optional<Piggy> lockActive(@Param("id") String id);
}
