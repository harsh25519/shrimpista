package bdj.hkb.urlShortner.click;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    Page<ClickEvent> findByUrlIdOrderByClickedAtDesc(Long urlId, Pageable pageable);

    @Modifying
    @Query(value = """
        DELETE FROM click_events WHERE id IN (
            SELECT id FROM click_events
            WHERE clicked_at < :cutoff
            LIMIT :batchSize
        )
        """, nativeQuery = true)
    int deleteOldBatch(@Param("cutoff") OffsetDateTime cutoff,
                       @Param("batchSize") int batchSize);
}
