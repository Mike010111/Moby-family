package by.moby.rewards.points;

import by.moby.rewards.platform.security.AccessPolicy;
import by.moby.rewards.platform.security.CurrentUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PointsController {

    record PointsView(String memberId, int points) {
    }

    private final PointsService points;
    private final AccessPolicy access;

    PointsController(PointsService points, AccessPolicy access) {
        this.points = points;
        this.access = access;
    }

    /** Свои баллы: у родителя — свой счёт баллов, у ребёнка — свой. */
    @GetMapping("/api/rewards/me/points")
    @Transactional(readOnly = true)
    PointsView mine() {
        CurrentUser user = access.current();
        return new PointsView(user.memberId(), points.account(user.memberId()).getPoints());
    }
}
