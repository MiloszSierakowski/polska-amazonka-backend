package pl.polskaamazonka.backend.mapper;

import pl.polskaamazonka.backend.dto.LinkDTO;
import pl.polskaamazonka.backend.model.Link;

public class LinkMapper {

    public static LinkDTO toDTO(Link link) {
        if (link == null) return null;

        LinkDTO dto = new LinkDTO();
        dto.setId(link.getId() != null ? link.getId().longValue() : null);
        dto.setUrl(link.getUrl());
        dto.setType(link.getType());
        dto.setIsActive(link.getIsActive());
        return dto;
    }
}
