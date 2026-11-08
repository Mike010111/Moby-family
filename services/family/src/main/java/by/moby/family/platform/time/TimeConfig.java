package by.moby.family.platform.time;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** Время — только через внедрённый {@link Clock}: календарные периоды считаются в Europe/Minsk. */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    public static final ZoneId ZONE = ZoneId.of("Europe/Minsk");

    @Bean
    Clock clock() {
        return Clock.system(ZONE);
    }
}
