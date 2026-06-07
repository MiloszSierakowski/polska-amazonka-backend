package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.polskaamazonka.backend.dto.AffiliateCodeDTO;
import pl.polskaamazonka.backend.exception.ActiveAffiliateCodeConflictException;
import pl.polskaamazonka.backend.mapper.AffiliateCodeMapper;
import pl.polskaamazonka.backend.model.AffiliateCode;
import pl.polskaamazonka.backend.model.Shop;
import pl.polskaamazonka.backend.model.enums.AffiliateCodeType;
import pl.polskaamazonka.backend.repository.AffiliateCodeRepository;
import pl.polskaamazonka.backend.repository.ShopRepository;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AffiliateCodeCrudService {

    private static final String ACTIVE_AFFILIATE_CODE_CONFLICT_MESSAGE =
            "Istnieje już aktywny kod afiliacyjny dla tej platformy. Zmodyfikuj go lub najpierw dezaktywuj.";

    private final AffiliateCodeRepository affiliateCodeRepository;
    private final ShopRepository shopRepository;

    @Transactional(readOnly = true)
    public List<AffiliateCodeDTO> getAll() {
        return affiliateCodeRepository.findAllByTypeOrderByCreatedAtDesc(AffiliateCodeType.AFFILIATE).stream()
                .map(AffiliateCodeMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public AffiliateCodeDTO getById(Long id) {
        return affiliateCodeRepository.findByIdAndType(id, AffiliateCodeType.AFFILIATE)
                .map(AffiliateCodeMapper::toDTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional
    public AffiliateCodeDTO create(AffiliateCodeDTO dto) {
        validatePayload(dto);
        Shop shop = resolveShop(dto.getShopId());
        boolean isActive = dto.getIsActive() != null ? dto.getIsActive() : Boolean.TRUE;
        validateUniqueActiveAffiliateCode(shop, isActive, null);
        AffiliateCode entity = new AffiliateCode();
        entity.setShop(shop);
        entity.setCodeValue(dto.getCodeValue().trim());
        entity.setDescription(normalizeDescription(dto.getDescription()));
        entity.setType(AffiliateCodeType.AFFILIATE);
        entity.setIsActive(isActive);
        entity.setCreatedAt(Instant.now());
        AffiliateCode saved = affiliateCodeRepository.save(entity);
        return AffiliateCodeMapper.toDTO(saved);
    }

    @Transactional
    public AffiliateCodeDTO update(Long id, AffiliateCodeDTO dto) {
        validatePayload(dto);
        AffiliateCode entity = affiliateCodeRepository.findByIdAndType(id, AffiliateCodeType.AFFILIATE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Shop shop = resolveShop(dto.getShopId());
        boolean isActive = dto.getIsActive() != null ? dto.getIsActive() : Boolean.TRUE;
        validateUniqueActiveAffiliateCode(shop, isActive, id);
        entity.setShop(shop);
        entity.setCodeValue(dto.getCodeValue().trim());
        entity.setDescription(normalizeDescription(dto.getDescription()));
        entity.setIsActive(isActive);
        AffiliateCode saved = affiliateCodeRepository.save(entity);
        return AffiliateCodeMapper.toDTO(saved);
    }

    @Transactional
    public void delete(Long id) {
        AffiliateCode entity = affiliateCodeRepository.findByIdAndType(id, AffiliateCodeType.AFFILIATE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        affiliateCodeRepository.delete(entity);
    }

    private void validateUniqueActiveAffiliateCode(Shop shop, boolean isActive, Long excludeId) {
        if (!isActive) {
            return;
        }
        boolean conflictExists = excludeId == null
                ? affiliateCodeRepository.findFirstByShopAndTypeAndIsActiveTrue(
                        shop,
                        AffiliateCodeType.AFFILIATE
                ).isPresent()
                : affiliateCodeRepository.findFirstByShopAndTypeAndIsActiveTrueAndIdNot(
                        shop,
                        AffiliateCodeType.AFFILIATE,
                        excludeId
                ).isPresent();
        if (conflictExists) {
            throw new ActiveAffiliateCodeConflictException(ACTIVE_AFFILIATE_CODE_CONFLICT_MESSAGE);
        }
    }

    private void validatePayload(AffiliateCodeDTO dto) {
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        if (dto.getShopId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        if (dto.getCodeValue() == null || dto.getCodeValue().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    private Shop resolveShop(Long shopId) {
        return shopRepository.findById(shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
