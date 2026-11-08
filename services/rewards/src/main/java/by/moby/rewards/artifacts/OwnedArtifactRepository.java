package by.moby.rewards.artifacts;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OwnedArtifactRepository extends JpaRepository<OwnedArtifact, Long> {

    List<OwnedArtifact> findByChildId(String childId);

    Optional<OwnedArtifact> findByChildIdAndSlot(String childId, Slot slot);

    Optional<OwnedArtifact> findByChildIdAndArtifactId(String childId, String artifactId);
}
