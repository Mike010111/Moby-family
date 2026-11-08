package by.moby.family.requests.api;

import by.moby.common.money.Money;

import java.time.Instant;

public record RequestView(String id, String kind, String childId, String relativeId, Money amount, String reason,
                          String contactName, String piggyId, String status, String resolution, Instant ts, Instant resolvedAt) {
}
