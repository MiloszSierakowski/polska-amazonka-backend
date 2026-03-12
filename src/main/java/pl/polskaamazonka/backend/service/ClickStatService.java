package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.polskaamazonka.backend.model.Clickstat;
import pl.polskaamazonka.backend.repository.ClickStatRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClickStatService {

    private final ClickStatRepository clickStatRepository;

    public List<Clickstat> getAll() {
        return clickStatRepository.findAll();
    }

    public Clickstat getById(Integer id) {
        return clickStatRepository.findById(id).orElse(null);
    }
}
