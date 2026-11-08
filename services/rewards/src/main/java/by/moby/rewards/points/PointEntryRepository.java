package by.moby.rewards.points;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointEntryRepository extends JpaRepository<PointEntry, Long> {

    @Query("select coalesce(sum(e.delta), 0) from PointEntry e where e.memberId = :memberId")
    long sumFor(@Param("memberId") String memberId);
}
