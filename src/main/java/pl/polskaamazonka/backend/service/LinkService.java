package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.polskaamazonka.backend.dto.LinkDTO;
import pl.polskaamazonka.backend.mapper.LinkMapper;
import pl.polskaamazonka.backend.repository.LinkRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LinkService {

    private final LinkRepository linkRepository;

    public List<LinkDTO> getAll() {
        return linkRepository.findAll().stream()
                .map(LinkMapper::toDTO)
                .toList();
    }

    public LinkDTO getById(Long id) {
        return linkRepository.findById(id)
                .map(LinkMapper::toDTO)
                .orElse(null);
    }

    public List<LinkDTO> getByType(String type) {
        return linkRepository.findByType(type).stream()
                .map(LinkMapper::toDTO)
                .toList();
    }

}
