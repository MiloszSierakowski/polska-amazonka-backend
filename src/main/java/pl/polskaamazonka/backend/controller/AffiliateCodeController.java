package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pl.polskaamazonka.backend.dto.AffiliateCodeDTO;
import pl.polskaamazonka.backend.service.AffiliateCodeCrudService;

import java.util.List;

@RestController
@RequestMapping("/api/affiliate-codes")
@RequiredArgsConstructor
public class AffiliateCodeController {

    private final AffiliateCodeCrudService affiliateCodeCrudService;

    @GetMapping
    public List<AffiliateCodeDTO> getAll() {
        return affiliateCodeCrudService.getAll();
    }

    @PutMapping("/reorder")
    public void reorder(@RequestBody List<Long> orderedIds) {
        affiliateCodeCrudService.reorder(orderedIds);
    }

    @GetMapping("/{id}")
    public AffiliateCodeDTO getById(@PathVariable Long id) {
        return affiliateCodeCrudService.getById(id);
    }

    @PostMapping
    public AffiliateCodeDTO create(@RequestBody AffiliateCodeDTO dto) {
        return affiliateCodeCrudService.create(dto);
    }

    @PutMapping("/{id}")
    public AffiliateCodeDTO update(@PathVariable Long id, @RequestBody AffiliateCodeDTO dto) {
        return affiliateCodeCrudService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        affiliateCodeCrudService.delete(id);
    }
}
