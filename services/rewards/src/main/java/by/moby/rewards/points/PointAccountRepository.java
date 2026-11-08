package by.moby.rewards.points;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PointAccountRepository extends JpaRepository<PointAccount, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PointAccount a where a.memberId = :memberId")
    Optional<PointAccount> lock(@Param("memberId") String memberId);
}
