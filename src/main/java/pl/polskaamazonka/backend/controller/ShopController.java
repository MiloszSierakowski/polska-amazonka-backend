package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.polskaamazonka.backend.dto.ShopDTO;
import pl.polskaamazonka.backend.service.ShopService;

import java.util.List;

@RestController
@RequestMapping("/api/shops")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;

    @GetMapping
    public List<ShopDTO> getAll(@RequestParam(name = "activeOnly", required = false) Boolean activeOnly) {
        if (activeOnly == null || activeOnly) {
            return shopService.getAllActive();
        }
        return shopService.getAll();
    }

    @GetMapping("/{id}")
    public ShopDTO getById(@PathVariable Long id) {
        return shopService.getById(id);
    }

    @PostMapping
    public ShopDTO create(@RequestBody ShopDTO dto) {
        return shopService.create(dto);
    }

    @PutMapping("/{id}")
    public ShopDTO update(@PathVariable Long id, @RequestBody ShopDTO dto) {
        return shopService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        shopService.delete(id);
    }
}
