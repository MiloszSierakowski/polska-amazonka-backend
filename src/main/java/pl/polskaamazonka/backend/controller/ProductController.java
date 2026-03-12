package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pl.polskaamazonka.backend.dto.ProductDTO;
import pl.polskaamazonka.backend.service.ProductService;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public List<ProductDTO> getAll() {
        return productService.getAll();
    }

    @GetMapping("/{id}")
    public ProductDTO getById(@PathVariable Integer id) {
        return productService.getById(id);
    }
}
