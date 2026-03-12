package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.polskaamazonka.backend.dto.VideoDTO;
import pl.polskaamazonka.backend.mapper.VideoMapper;
import pl.polskaamazonka.backend.repository.VideoRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;

    public List<VideoDTO> getAll() {
        return videoRepository.findAll().stream()
                .map(VideoMapper::toDTO)
                .toList();
    }

    public VideoDTO getById(Integer id) {
        return videoRepository.findById(id)
                .map(VideoMapper::toDTO)
                .orElse(null);
    }
}
