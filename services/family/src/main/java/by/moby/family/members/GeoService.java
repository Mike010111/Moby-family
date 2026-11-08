package by.moby.family.members;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.random.RandomGenerator;

/** {@code parent/refreshGeo}: в демо — случайная другая точка радара и разряд батареи. Работает при блокировке счёта. */
@Service
public class GeoService {

    public record Place(String name, double x, double y, double distance) {
    }

    /** GEO_PLACES из catalog.js. */
    public static final List<Place> PLACES = List.of(
            new Place("Дом", 0.5, 0.5, 0),
            new Place("Школа №12", 0.68, 0.32, 1.2),
            new Place("Спортшкола «Олимп»", 0.27, 0.36, 2.1),
            new Place("Библиотека", 0.34, 0.72, 1.6),
            new Place("Торговый центр", 0.76, 0.7, 2.8));

    private final Members members;
    private final RandomGenerator random;
    private final Clock clock;

    public GeoService(Members members, RandomGenerator random, Clock clock) {
        this.members = members;
        this.random = random;
        this.clock = clock;
    }

    @Transactional
    public Member refresh(String childId) {
        Member child = members.managed(childId);
        List<Place> options = PLACES.stream().filter(p -> !p.name().equals(child.getGeo().getPlace())).toList();
        Place next = options.get(random.nextInt(options.size()));
        int battery = Math.max(5, child.getBattery() - 1 - random.nextInt(4));
        child.moveTo(new Geo(next.name(), next.x(), next.y(), next.distance(), Instant.now(clock)), battery);
        return child;
    }
}
