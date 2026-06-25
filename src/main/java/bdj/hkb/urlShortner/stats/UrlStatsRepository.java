package bdj.hkb.urlShortner.stats;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UrlStatsRepository extends JpaRepository<UrlStats, Long> {
    @Modifying
    @Query(value = """
        INSERT INTO url_statistics (url_id, total_clicks, unique_visitors, last_updated_at)
        VALUES (:urlId, :clickCount, 1, NOW())
        ON CONFLICT (url_id) 
        DO UPDATE SET total_clicks = url_statistics.total_clicks + :clickCount,
                      last_updated_at = NOW()
        """, nativeQuery = true)
    void incrementClicksByCount(@Param("urlId") Long urlId, @Param("clickCount") Long clickCount);
}
