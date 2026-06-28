package pl.polskaamazonka.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.polskaamazonka.backend.dto.AffiliateCodeDTO;
import pl.polskaamazonka.backend.exception.ActiveAffiliateCodeConflictException;
import pl.polskaamazonka.backend.model.AffiliateCode;
import pl.polskaamazonka.backend.model.Shop;
import pl.polskaamazonka.backend.model.enums.AffiliateCodeType;
import pl.polskaamazonka.backend.repository.AffiliateCodeRepository;
import pl.polskaamazonka.backend.repository.ShopRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AffiliateCodeCrudServiceTest {

    @Mock
    private AffiliateCodeRepository affiliateCodeRepository;
    @Mock
    private ShopRepository shopRepository;
    @Mock
    private AffiliateCodeDisplayOrderService displayOrderService;
    @Mock
    private ActivityLogService activityLogService;

    @InjectMocks
    private AffiliateCodeCrudService affiliateCodeCrudService;

    private Shop shop;
    private AffiliateCode existingCode;

    @BeforeEach
    void setUp() {
        shop = new Shop();
        shop.setId(5L);
        shop.setSlug("temu");
        shop.setName("Temu");

        existingCode = new AffiliateCode();
        existingCode.setId(7L);
        existingCode.setShop(shop);
        existingCode.setCodeValue("OLD");
        existingCode.setType(AffiliateCodeType.AFFILIATE);
        existingCode.setIsActive(true);
        existingCode.setDisplayOrder(0L);
    }

    @Test
    void createPersistsAffiliateCode() {
        AffiliateCodeDTO dto = new AffiliateCodeDTO();
        dto.setShopId(5L);
        dto.setCodeValue("NEWCODE");
        dto.setIsActive(true);

        when(shopRepository.findById(5L)).thenReturn(Optional.of(shop));
        when(affiliateCodeRepository.findFirstByShopAndTypeAndIsActiveTrue(shop, AffiliateCodeType.AFFILIATE))
                .thenReturn(Optional.empty());
        when(displayOrderService.nextDisplayOrder(AffiliateCodeType.AFFILIATE)).thenReturn(1L);
        when(affiliateCodeRepository.save(any(AffiliateCode.class))).thenAnswer(invocation -> {
            AffiliateCode saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });

        AffiliateCodeDTO result = affiliateCodeCrudService.create(dto);

        ArgumentCaptor<AffiliateCode> captor = ArgumentCaptor.forClass(AffiliateCode.class);
        verify(affiliateCodeRepository).save(captor.capture());
        assertEquals("NEWCODE", result.getCodeValue());
        assertEquals(AffiliateCodeType.AFFILIATE, captor.getValue().getType());
    }

    @Test
    void createActiveAffiliateCodeConflictThrows() {
        AffiliateCodeDTO dto = new AffiliateCodeDTO();
        dto.setShopId(5L);
        dto.setCodeValue("NEWCODE");
        dto.setIsActive(true);

        when(shopRepository.findById(5L)).thenReturn(Optional.of(shop));
        when(affiliateCodeRepository.findFirstByShopAndTypeAndIsActiveTrue(shop, AffiliateCodeType.AFFILIATE))
                .thenReturn(Optional.of(existingCode));

        assertThrows(ActiveAffiliateCodeConflictException.class, () -> affiliateCodeCrudService.create(dto));
    }

    @Test
    void reorderDelegatesToDisplayOrderService() {
        List<Long> orderedIds = List.of(7L, 8L);
        affiliateCodeCrudService.reorder(orderedIds);
        verify(displayOrderService).reorder(orderedIds, AffiliateCodeType.AFFILIATE);
    }

    @Test
    void deleteRemovesAffiliateCode() {
        when(affiliateCodeRepository.findByIdAndType(7L, AffiliateCodeType.AFFILIATE))
                .thenReturn(Optional.of(existingCode));

        affiliateCodeCrudService.delete(7L);

        verify(affiliateCodeRepository).delete(existingCode);
    }
}
