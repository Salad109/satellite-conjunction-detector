package io.salad109.conjunctionapi.conjunction.internal;

import java.util.List;

public interface ConjunctionRepositoryCustom {
    void batchUpsertIfCloser(List<Conjunction> conjunctions);
}