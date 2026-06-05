package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.polskaamazonka.backend.dto.AffiliateCodeDTO;
import pl.polskaamazonka.backend.mapper.AffiliateCodeMapper;
import pl.polskaamazonka.backend.model.AffiliateCode;
import pl.polskaamazonka.backend.model.enums.AffiliateCodeType;
import pl.polskaamazonka.backend.model.enums.Platform;
import pl.polskaamazonka.backend.repository.AffiliateCodeRepository;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AffiliateCodeCrudService {

    private final AffiliateCodeRepository affiliateCodeRepository;

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
        AffiliateCode entity = new AffiliateCode();
        entity.setPlatform(parsePlatform(dto.getPlatform()));
        entity.setCodeValue(dto.getCodeValue().trim());
        entity.setDescription(normalizeDescription(dto.getDescription()));
        entity.setType(AffiliateCodeType.AFFILIATE);
        entity.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : Boolean.TRUE);
        entity.setCreatedAt(Instant.now());
        AffiliateCode saved = affiliateCodeRepository.save(entity);
        return AffiliateCodeMapper.toDTO(saved);
    }

    @Transactional
    public AffiliateCodeDTO update(Long id, AffiliateCodeDTO dto) {
        validatePayload(dto);
        AffiliateCode entity = affiliateCodeRepository.findByIdAndType(id, AffiliateCodeType.AFFILIATE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        entity.setPlatform(parsePlatform(dto.getPlatform()));
        entity.setCodeValue(dto.getCodeValue().trim());
        entity.setDescription(normalizeDescription(dto.getDescription()));
        entity.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : Boolean.TRUE);
        AffiliateCode saved = affiliateCodeRepository.save(entity);
        return AffiliateCodeMapper.toDTO(saved);
    }

    private void validatePayload(AffiliateCodeDTO dto) {
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        if (dto.getPlatform() == null || dto.getPlatform().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        if (dto.getCodeValue() == null || dto.getCodeValue().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Platform parsePlatform(String platform) {
        try {
            return Platform.valueOf(platform.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }
}
