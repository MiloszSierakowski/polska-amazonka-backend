package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.polskaamazonka.backend.dto.DiscountCodeDTO;
import pl.polskaamazonka.backend.dto.PublicDiscountCodeDTO;
import pl.polskaamazonka.backend.mapper.DiscountCodeMapper;
import pl.polskaamazonka.backend.model.AffiliateCode;
import pl.polskaamazonka.backend.model.Shop;
import pl.polskaamazonka.backend.model.enums.AffiliateCodeType;
import pl.polskaamazonka.backend.repository.AffiliateCodeRepository;
import pl.polskaamazonka.backend.repository.ShopRepository;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DiscountCodeService {

    private final AffiliateCodeRepository affiliateCodeRepository;
    private final ShopRepository shopRepository;
    private final AffiliateCodeDisplayOrderService displayOrderService;

    @Transactional(readOnly = true)
    public List<DiscountCodeDTO> getAll() {
        return affiliateCodeRepository.findAllByTypeOrderByDisplayOrderAscIdAsc(AffiliateCodeType.DISCOUNT).stream()
                .map(DiscountCodeMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PublicDiscountCodeDTO> getActivePublic() {
        return affiliateCodeRepository.findAllByTypeAndIsActiveTrueOrderByDisplayOrderAscIdAsc(AffiliateCodeType.DISCOUNT).stream()
                .map(DiscountCodeMapper::toPublicDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public DiscountCodeDTO getById(Long id) {
        return affiliateCodeRepository.findByIdAndType(id, AffiliateCodeType.DISCOUNT)
                .map(DiscountCodeMapper::toDTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional
    public DiscountCodeDTO create(DiscountCodeDTO dto) {
        validatePayload(dto);
        Shop shop = resolveShop(dto.getShopId());
        AffiliateCode entity = new AffiliateCode();
        entity.setShop(shop);
        entity.setCodeValue(dto.getCodeValue().trim());
        entity.setDescription(normalizeDescription(dto.getDescription()));
        entity.setType(AffiliateCodeType.DISCOUNT);
        entity.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : Boolean.TRUE);
        entity.setCreatedAt(Instant.now());
        entity.setDisplayOrder(displayOrderService.nextDisplayOrder(AffiliateCodeType.DISCOUNT));
        AffiliateCode saved = affiliateCodeRepository.save(entity);
        return DiscountCodeMapper.toDTO(saved);
    }

    @Transactional
    public DiscountCodeDTO update(Long id, DiscountCodeDTO dto) {
        validatePayload(dto);
        AffiliateCode entity = affiliateCodeRepository.findByIdAndType(id, AffiliateCodeType.DISCOUNT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Shop shop = resolveShop(dto.getShopId());
        entity.setShop(shop);
        entity.setCodeValue(dto.getCodeValue().trim());
        entity.setDescription(normalizeDescription(dto.getDescription()));
        entity.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : Boolean.TRUE);
        AffiliateCode saved = affiliateCodeRepository.save(entity);
        return DiscountCodeMapper.toDTO(saved);
    }

    @Transactional
    public void delete(Long id) {
        AffiliateCode entity = affiliateCodeRepository.findByIdAndType(id, AffiliateCodeType.DISCOUNT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        affiliateCodeRepository.delete(entity);
    }

    @Transactional
    public void reorder(List<Long> orderedIds) {
        displayOrderService.reorder(orderedIds, AffiliateCodeType.DISCOUNT);
    }

    private void validatePayload(DiscountCodeDTO dto) {
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        if (dto.getShopId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        if (dto.getCodeValue() == null || dto.getCodeValue().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        if (dto.getDescription() == null || dto.getDescription().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    private Shop resolveShop(Long shopId) {
        return shopRepository.findById(shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
    }

    private String normalizeDescription(String description) {
        return description.trim();
    }
}
