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
import pl.polskaamazonka.backend.model.enums.AffiliateCodeType;
import pl.polskaamazonka.backend.model.enums.Platform;
import pl.polskaamazonka.backend.repository.AffiliateCodeRepository;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DiscountCodeService {

    private final AffiliateCodeRepository affiliateCodeRepository;

    @Transactional(readOnly = true)
    public List<DiscountCodeDTO> getAll() {
        return affiliateCodeRepository.findAllByTypeOrderByCreatedAtDesc(AffiliateCodeType.DISCOUNT).stream()
                .map(DiscountCodeMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PublicDiscountCodeDTO> getActivePublic() {
        return affiliateCodeRepository.findAllByTypeAndIsActiveTrueOrderByCreatedAtDesc(AffiliateCodeType.DISCOUNT).stream()
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
        AffiliateCode entity = new AffiliateCode();
        entity.setPlatform(parsePlatform(dto.getPlatform()));
        entity.setCodeValue(dto.getCodeValue().trim());
        entity.setDescription(normalizeDescription(dto.getDescription()));
        entity.setType(AffiliateCodeType.DISCOUNT);
        entity.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : Boolean.TRUE);
        entity.setCreatedAt(Instant.now());
        AffiliateCode saved = affiliateCodeRepository.save(entity);
        return DiscountCodeMapper.toDTO(saved);
    }

    @Transactional
    public DiscountCodeDTO update(Long id, DiscountCodeDTO dto) {
        validatePayload(dto);
        AffiliateCode entity = affiliateCodeRepository.findByIdAndType(id, AffiliateCodeType.DISCOUNT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        entity.setPlatform(parsePlatform(dto.getPlatform()));
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

    private void validatePayload(DiscountCodeDTO dto) {
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        if (dto.getPlatform() == null || dto.getPlatform().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        if (dto.getCodeValue() == null || dto.getCodeValue().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        if (dto.getDescription() == null || dto.getDescription().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeDescription(String description) {
        return description.trim();
    }

    private Platform parsePlatform(String platform) {
        try {
            return Platform.valueOf(platform.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }
}
