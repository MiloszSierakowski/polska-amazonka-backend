package pl.polskaamazonka.backend.mapper;

import pl.polskaamazonka.backend.dto.AffiliateCodeDTO;
import pl.polskaamazonka.backend.model.AffiliateCode;

public class AffiliateCodeMapper {

    public static AffiliateCodeDTO toDTO(AffiliateCode entity) {
        if (entity == null) {
            return null;
        }
        AffiliateCodeDTO dto = new AffiliateCodeDTO();
        dto.setId(entity.getId());
        dto.setPlatform(entity.getPlatform() != null ? entity.getPlatform().name() : null);
        dto.setCodeValue(entity.getCodeValue());
        dto.setDescription(entity.getDescription());
        dto.setIsActive(entity.getIsActive());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
