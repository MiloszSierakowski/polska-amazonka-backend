package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pl.polskaamazonka.backend.dto.DiscountCodeDTO;
import pl.polskaamazonka.backend.service.DiscountCodeService;

import java.util.List;

@RestController
@RequestMapping("/api/discount-codes")
@RequiredArgsConstructor
public class DiscountCodeController {

    private final DiscountCodeService discountCodeService;

    @GetMapping
    public List<DiscountCodeDTO> getAll() {
        return discountCodeService.getAll();
    }

    @GetMapping("/{id}")
    public DiscountCodeDTO getById(@PathVariable Long id) {
        return discountCodeService.getById(id);
    }

    @PostMapping
    public DiscountCodeDTO create(@RequestBody DiscountCodeDTO dto) {
        return discountCodeService.create(dto);
    }

    @PutMapping("/{id}")
    public DiscountCodeDTO update(@PathVariable Long id, @RequestBody DiscountCodeDTO dto) {
        return discountCodeService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        discountCodeService.delete(id);
    }
}
