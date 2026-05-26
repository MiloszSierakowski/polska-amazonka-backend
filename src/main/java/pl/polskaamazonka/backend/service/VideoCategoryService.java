package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.polskaamazonka.backend.model.Videocategory;
import pl.polskaamazonka.backend.repository.VideoCategoryRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VideoCategoryService {

    private final VideoCategoryRepository videoCategoryRepository;

    public List<Videocategory> getAll() {
        return videoCategoryRepository.findAll();
    }

    public Videocategory getById(Long id) {
        return videoCategoryRepository.findById(id).orElse(null);
    }
}
