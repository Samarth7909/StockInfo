package com.indira.opsconsole.repository;

import com.indira.opsconsole.domain.entity.ExchangeReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExchangeReferenceRepository extends JpaRepository<ExchangeReference, String> {
    List<ExchangeReference> findByIsin(String isin);
    List<ExchangeReference> findBySourceFileId(String sourceFileId);
}
