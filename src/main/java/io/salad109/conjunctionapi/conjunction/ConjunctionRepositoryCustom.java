package io.salad109.conjunctionapi.conjunction;

import java.util.List;

public interface ConjunctionRepositoryCustom {
    void batchUpsertIfCloser(List<Conjunction> conjunctions);
}