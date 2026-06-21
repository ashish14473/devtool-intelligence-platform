package com.devtools.intelligence.repository;
import com.devtools.intelligence.model.EmergingIssueEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
@Repository
public interface EmergingIssueRepository extends JpaRepository<EmergingIssueEntity, Long> {
    List<EmergingIssueEntity> findAllByOrderByDetectedDateDescTicketCountDesc();
    void deleteByDetectedDate(LocalDate date);
}
