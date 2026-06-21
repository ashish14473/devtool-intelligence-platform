package com.devtools.intelligence.repository;
import com.devtools.intelligence.model.PainClusterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface PainClusterRepository extends JpaRepository<PainClusterEntity, Long> {
    List<PainClusterEntity> findAllByOrderByTicketCountDesc();
}
