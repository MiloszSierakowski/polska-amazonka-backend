package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.polskaamazonka.backend.model.AffiliateCode;
import pl.polskaamazonka.backend.model.enums.AffiliateCodeType;
import pl.polskaamazonka.backend.repository.AffiliateCodeRepository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AffiliateCodeDisplayOrderService {

    private final AffiliateCodeRepository affiliateCodeRepository;

    @Transactional(readOnly = true)
    public Long nextDisplayOrder(AffiliateCodeType type) {
        return affiliateCodeRepository.countByType(type);
    }

    @Transactional
    public void reorder(List<Long> orderedIds, AffiliateCodeType type) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        Set<Long> uniqueIds = new HashSet<>(orderedIds);
        if (uniqueIds.size() != orderedIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        long typeCount = affiliateCodeRepository.countByType(type);
        if (orderedIds.size() != typeCount) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        List<AffiliateCode> codes = affiliateCodeRepository.findAllById(orderedIds);
        if (codes.size() != orderedIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        Map<Long, AffiliateCode> byId = new HashMap<>();
        for (AffiliateCode code : codes) {
            if (code.getType() != type) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }
            byId.put(code.getId(), code);
        }
        for (int index = 0; index < orderedIds.size(); index++) {
            AffiliateCode code = byId.get(orderedIds.get(index));
            if (code == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }
            code.setDisplayOrder((long) index);
        }
        affiliateCodeRepository.saveAll(codes);
    }
}
