package pl.polskaamazonka.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.polskaamazonka.backend.model.AffiliateCode;
import pl.polskaamazonka.backend.model.enums.AffiliateCodeType;
import pl.polskaamazonka.backend.model.enums.Platform;

import java.util.Optional;

public interface AffiliateCodeRepository extends JpaRepository<AffiliateCode, Integer> {

    Optional<AffiliateCode> findFirstByPlatformAndTypeAndIsActiveTrue(
            Platform platform,
            AffiliateCodeType type
    );
}
