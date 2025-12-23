package io.salad109.conjunctionapi.conjunction.internal;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConjunctionRepository extends JpaRepository<Conjunction, Integer>, ConjunctionRepositoryCustom {
}
