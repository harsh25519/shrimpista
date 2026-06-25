package bdj.hkb.urlShortner.click;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {
    @Modifying
    @Query(value = """
        INSERT INTO url_statistics (url_id, total_clicks, unique_visitors, last_updated_at)
        VALUES (:urlId, 1, 1, NOW())
        ON CONFLICT (url_id) 
        DO UPDATE SET total_clicks = url_statistics.total_clicks + 1,
                      last_updated_at = NOW()
        """, nativeQuery = true)
    void incrementClicks(@Param("urlId") Long urlId);

    Page<ClickEvent> findByUrlIdOrderByClickedAtDesc(Long urlId, Pageable pageable);
}
