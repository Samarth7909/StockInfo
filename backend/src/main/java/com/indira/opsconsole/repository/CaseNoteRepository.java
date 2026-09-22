package com.indira.opsconsole.repository;

import com.indira.opsconsole.domain.entity.CaseNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CaseNoteRepository extends JpaRepository<CaseNote, String> {
    @EntityGraph(attributePaths = "author")
    List<CaseNote> findByExceptionCaseIdOrderByCreatedAtAsc(String caseId);
}
