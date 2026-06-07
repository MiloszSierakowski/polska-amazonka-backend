package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.polskaamazonka.backend.dto.ShopDTO;
import pl.polskaamazonka.backend.mapper.ShopMapper;
import pl.polskaamazonka.backend.repository.ShopRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopRepository shopRepository;

    @Transactional(readOnly = true)
    public List<ShopDTO> getAllActive() {
        return shopRepository.findAllByIsActiveTrueOrderByNameAsc().stream()
                .map(ShopMapper::toDTO)
                .toList();
    }
}
