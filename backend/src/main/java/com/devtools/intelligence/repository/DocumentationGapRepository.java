package com.devtools.intelligence.repository;
import com.devtools.intelligence.model.DocumentationGapEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface DocumentationGapRepository extends JpaRepository<DocumentationGapEntity, Long> {
    List<DocumentationGapEntity> findAllByOrderByPriorityScoreDesc();
}
