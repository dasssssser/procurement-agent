package org.example.repository;

import org.example.entity.CpItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface CpItemRepository extends JpaRepository<CpItem, UUID> {

    @Query("SELECT c.supplierName, MIN(c.price) as minPrice " +
            "FROM CpItem c " +
            "WHERE LOWER(c.productName) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "GROUP BY c.supplierName " +
            "ORDER BY minPrice ASC " +
            "LIMIT 5")
    List<Object[]> findTop5Suppliers(@Param("query") String query);

    List<CpItem> findByTaskId(UUID taskId);
}