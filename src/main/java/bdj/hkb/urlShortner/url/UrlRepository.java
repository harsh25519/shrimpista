package bdj.hkb.urlShortner.url;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UrlRepository extends JpaRepository<Url, Long> {

    Optional<Url> findByShortCode(String shortCode);

    Optional<Url> findByLongUrlHash(String longUrlHash);

    Page<Url> findAllByUserIdOrderByCreatedAtDesc(UUID userId, PageRequest pageRequest);

    Optional<Url> findByLongUrlHashAndUserId(String urlHash, UUID userId);

    Optional<Url> findByLongUrlHashAndUserIdIsNull(String urlHash);
}
