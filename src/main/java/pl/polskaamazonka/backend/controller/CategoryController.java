package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pl.polskaamazonka.backend.dto.CategoryDTO;
import pl.polskaamazonka.backend.service.CategoryService;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public List<CategoryDTO> getAll() {
        return categoryService.getAll();
    }

    @GetMapping("/{id}")
    public CategoryDTO getById(@PathVariable Integer id) {
        return categoryService.getById(id);
    }
}
