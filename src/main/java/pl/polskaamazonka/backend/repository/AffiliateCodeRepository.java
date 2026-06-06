package pl.polskaamazonka.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.polskaamazonka.backend.model.AffiliateCode;
import pl.polskaamazonka.backend.model.enums.AffiliateCodeType;
import pl.polskaamazonka.backend.model.enums.Platform;

import java.util.List;
import java.util.Optional;

public interface AffiliateCodeRepository extends JpaRepository<AffiliateCode, Long> {

    Optional<AffiliateCode> findFirstByPlatformAndTypeAndIsActiveTrue(
            Platform platform,
            AffiliateCodeType type
    );

    Optional<AffiliateCode> findFirstByPlatformAndTypeAndIsActiveTrueAndIdNot(
            Platform platform,
            AffiliateCodeType type,
            Long id
    );

    List<AffiliateCode> findAllByTypeOrderByCreatedAtDesc(AffiliateCodeType type);

    List<AffiliateCode> findAllByTypeAndIsActiveTrueOrderByCreatedAtDesc(AffiliateCodeType type);

    Optional<AffiliateCode> findByIdAndType(Long id, AffiliateCodeType type);
}
