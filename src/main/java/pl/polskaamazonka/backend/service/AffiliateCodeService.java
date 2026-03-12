package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.polskaamazonka.backend.model.AffiliateCode;
import pl.polskaamazonka.backend.model.enums.AffiliateCodeType;
import pl.polskaamazonka.backend.model.enums.Platform;
import pl.polskaamazonka.backend.repository.AffiliateCodeRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AffiliateCodeService {

    private final AffiliateCodeRepository repository;

    public Optional<AffiliateCode> getActiveAffiliateCode(Platform platform) {
        return repository.findFirstByPlatformAndTypeAndIsActiveTrue(
                platform,
                AffiliateCodeType.AFFILIATE
        );
    }

    public Optional<AffiliateCode> getActiveDiscountCode(Platform platform) {
        return repository.findFirstByPlatformAndTypeAndIsActiveTrue(
                platform,
                AffiliateCodeType.DISCOUNT
        );
    }
}
