package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.polskaamazonka.backend.dto.VideoProductDTO;
import pl.polskaamazonka.backend.mapper.VideoProductMapper;
import pl.polskaamazonka.backend.repository.VideoProductRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VideoProductService {

    private final VideoProductRepository repository;

    public List<VideoProductDTO> getAll() {
        return repository.findAll()
                .stream()
                .map(VideoProductMapper::toDTO)
                .toList();
    }
}
