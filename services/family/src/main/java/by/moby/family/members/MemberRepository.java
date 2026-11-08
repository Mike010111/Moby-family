package by.moby.family.members;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, String> {

    List<Member> findByFamilyIdOrderByPositionAsc(String familyId);

    Optional<Member> findFirstByFamilyIdAndMainTrue(String familyId);

    Optional<Member> findByIdAndKind(String id, MemberKind kind);
}
