package com.indira.opsconsole.repository;

import com.indira.opsconsole.domain.entity.CaseTransition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CaseTransitionRepository extends JpaRepository<CaseTransition, String> {
    @EntityGraph(attributePaths = "user")
    List<CaseTransition> findByExceptionCaseIdOrderByChangedAtAsc(String caseId);
}
