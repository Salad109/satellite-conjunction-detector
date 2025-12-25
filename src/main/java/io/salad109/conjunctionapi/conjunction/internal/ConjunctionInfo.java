package io.salad109.conjunctionapi.conjunction.internal;

import java.time.OffsetDateTime;

public record ConjunctionInfo(
        double missDistanceKm,
        OffsetDateTime tca,
        double relativeVelocityMS,
        Integer object1NoradId,
        String object1Name,
        String object1Type,
        Integer object2NoradId,
        String object2Name,
        String object2Type
) {
}
