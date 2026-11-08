plugins {
    id("moby.domain-service")
}

dependencies {
    // transactional outbox: Event Publication Registry (JPA) → Kafka
    implementation("org.springframework.modulith:spring-modulith-starter-jpa")
    implementation("org.springframework.modulith:spring-modulith-events-kafka")
}
