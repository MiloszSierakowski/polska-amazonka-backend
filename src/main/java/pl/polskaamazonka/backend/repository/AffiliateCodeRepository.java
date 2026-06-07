package pl.polskaamazonka.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.polskaamazonka.backend.model.AffiliateCode;
import pl.polskaamazonka.backend.model.Shop;
import pl.polskaamazonka.backend.model.enums.AffiliateCodeType;

import java.util.List;
import java.util.Optional;

public interface AffiliateCodeRepository extends JpaRepository<AffiliateCode, Long> {

    Optional<AffiliateCode> findFirstByShopAndTypeAndIsActiveTrue(
            Shop shop,
            AffiliateCodeType type
    );

    Optional<AffiliateCode> findFirstByShopAndTypeAndIsActiveTrueAndIdNot(
            Shop shop,
            AffiliateCodeType type,
            Long id
    );

    List<AffiliateCode> findAllByTypeOrderByDisplayOrderAscIdAsc(AffiliateCodeType type);

    List<AffiliateCode> findAllByTypeAndIsActiveTrueOrderByDisplayOrderAscIdAsc(AffiliateCodeType type);

    Optional<AffiliateCode> findByIdAndType(Long id, AffiliateCodeType type);

    long countByType(AffiliateCodeType type);
}
