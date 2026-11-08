package by.moby.notification.notifications;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, String> {

    List<Notification> findByFamilyIdAndAudienceOrderByCreatedAtDesc(String familyId, Audience audience, Limit limit);

    List<Notification> findByFamilyIdAndAudienceAndChildIdOrderByCreatedAtDesc(String familyId, Audience audience, String childId, Limit limit);

    List<Notification> findByFamilyIdAndAudienceAndChildIdAndSeenFalse(String familyId, Audience audience, String childId);
}
