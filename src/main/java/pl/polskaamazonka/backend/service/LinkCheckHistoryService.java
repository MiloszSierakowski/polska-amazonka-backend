package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.polskaamazonka.backend.model.Linkcheckhistory;
import pl.polskaamazonka.backend.repository.LinkCheckHistoryRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LinkCheckHistoryService {

    private final LinkCheckHistoryRepository linkCheckHistoryRepository;

    public List<Linkcheckhistory> getAll() {
        return linkCheckHistoryRepository.findAll();
    }

    public Linkcheckhistory getById(Integer id) {
        return linkCheckHistoryRepository.findById(id).orElse(null);
    }
}
