package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.polskaamazonka.backend.dto.PublicDiscountCodeDTO;
import pl.polskaamazonka.backend.service.DiscountCodeService;

import java.util.List;

@RestController
@RequestMapping("/api/public/discount-codes")
@RequiredArgsConstructor
public class PublicDiscountCodeController {

    private final DiscountCodeService discountCodeService;

    @GetMapping
    public List<PublicDiscountCodeDTO> getActive() {
        return discountCodeService.getActivePublic();
    }
}
