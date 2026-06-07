package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
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

    @PutMapping("/reorder")
    public void reorder(@RequestBody List<Long> orderedIds) {
        categoryService.reorder(orderedIds);
    }

    @GetMapping("/{id}")
    public CategoryDTO getById(@PathVariable Long id) {
        return categoryService.getById(id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CategoryDTO create(
            @RequestParam String name,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile
    ) {
        return categoryService.create(name, imageFile);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CategoryDTO update(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile
    ) {
        return categoryService.update(id, name, imageFile);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        categoryService.delete(id);
    }
}
