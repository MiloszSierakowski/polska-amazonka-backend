package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.polskaamazonka.backend.dto.QuickProductLinkValidationResult;
import pl.polskaamazonka.backend.dto.SupportedProductPlatform;
import pl.polskaamazonka.backend.service.scraper.AllegroUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.AliExpressUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.AmazonUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.TemuUrlNormalizer;

import java.net.URI;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class QuickProductLinkValidator {

    private final AllegroUrlNormalizer allegroUrlNormalizer;
    private final AliExpressUrlNormalizer aliExpressUrlNormalizer;
    private final TemuUrlNormalizer temuUrlNormalizer;
    private final AmazonUrlNormalizer amazonUrlNormalizer;

    public QuickProductLinkValidationResult validate(String url) {
        if (url == null || url.isBlank()) {
            return QuickProductLinkValidationResult.invalid(null, null, "Adres URL jest pusty.");
        }

        String trimmedUrl = url.trim();
        URI uri = parseUri(trimmedUrl);
        if (uri == null || uri.getHost() == null || uri.getHost().isBlank()) {
            return QuickProductLinkValidationResult.invalid(trimmedUrl, null, "Adres URL jest niepoprawny lub nieparsowalny.");
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            return QuickProductLinkValidationResult.invalid(
                    trimmedUrl,
                    null,
                    "Adres URL musi zaczynać się od http:// lub https://."
            );
        }

        SupportedProductPlatform platform = detectPlatform(trimmedUrl);
        if (platform == SupportedProductPlatform.UNKNOWN) {
            return QuickProductLinkValidationResult.unsupported(
                    trimmedUrl,
                    "Domena nie należy do obsługiwanych platform sklepowych."
            );
        }

        return switch (platform) {
            case ALLEGRO -> validateAllegro(trimmedUrl);
            case ALIEXPRESS -> validateAliExpress(trimmedUrl);
            case TEMU -> validateTemu(trimmedUrl);
            case AMAZON -> validateAmazon(trimmedUrl);
            default -> QuickProductLinkValidationResult.unsupported(
                    trimmedUrl,
                    "Domena nie należy do obsługiwanych platform sklepowych."
            );
        };
    }

    private QuickProductLinkValidationResult validateAllegro(String url) {
        String path = pathLower(url);
        if (path == null || path.isBlank() || "/".equals(path)) {
            return invalidForPlatform(url, SupportedProductPlatform.ALLEGRO, "Link Allegro nie wskazuje na kartę oferty.");
        }
        if (!path.contains("/oferta/")) {
            return invalidForPlatform(url, SupportedProductPlatform.ALLEGRO, "Link Allegro musi prowadzić do oferty (/oferta/).");
        }
        if (path.contains("/listing") || path.contains("/kategoria/")) {
            return invalidForPlatform(url, SupportedProductPlatform.ALLEGRO, "Link Allegro wygląda na wyszukiwanie lub kategorię, a nie ofertę.");
        }

        Long offerId = allegroUrlNormalizer.extractOfferId(url);
        if (offerId == null) {
            return invalidForPlatform(url, SupportedProductPlatform.ALLEGRO, "Nie udało się odczytać ID oferty Allegro z adresu URL.");
        }

        return QuickProductLinkValidationResult.valid(
                SupportedProductPlatform.ALLEGRO,
                url,
                allegroUrlNormalizer.normalize(url),
                String.valueOf(offerId)
        );
    }

    private QuickProductLinkValidationResult validateAliExpress(String url) {
        String path = pathLower(url);
        if (path == null || path.isBlank() || "/".equals(path)) {
            return invalidForPlatform(url, SupportedProductPlatform.ALIEXPRESS, "Link AliExpress nie wskazuje na kartę produktu.");
        }
        if (path.contains("/w/") || path.contains("/category/") || path.contains("/wholesale")) {
            return invalidForPlatform(
                    url,
                    SupportedProductPlatform.ALIEXPRESS,
                    "Link AliExpress wygląda na wyszukiwanie lub kategorię, a nie produkt."
            );
        }

        Long itemId = aliExpressUrlNormalizer.extractItemId(url);
        if (itemId == null) {
            return invalidForPlatform(
                    url,
                    SupportedProductPlatform.ALIEXPRESS,
                    "Link AliExpress musi zawierać /item/{id}.html."
            );
        }

        return QuickProductLinkValidationResult.valid(
                SupportedProductPlatform.ALIEXPRESS,
                url,
                aliExpressUrlNormalizer.normalize(url),
                String.valueOf(itemId)
        );
    }

    private QuickProductLinkValidationResult validateTemu(String url) {
        String path = pathLower(url);
        if (path == null || path.isBlank() || "/".equals(path)) {
            return invalidForPlatform(url, SupportedProductPlatform.TEMU, "Link Temu nie wskazuje na kartę produktu.");
        }
        if (path.contains("/category") || path.endsWith("/category.html")) {
            return invalidForPlatform(url, SupportedProductPlatform.TEMU, "Link Temu wygląda na kategorię, a nie produkt.");
        }
        if (path.contains("search_result") && temuUrlNormalizer.extractGoodsId(url) == null) {
            return invalidForPlatform(
                    url,
                    SupportedProductPlatform.TEMU,
                    "Link Temu search_result wymaga parametru goods_id."
            );
        }

        Long goodsId = temuUrlNormalizer.extractGoodsId(url);
        if (goodsId == null) {
            return invalidForPlatform(
                    url,
                    SupportedProductPlatform.TEMU,
                    "Link Temu musi zawierać ID produktu (-g-{id} lub goods_id)."
            );
        }

        return QuickProductLinkValidationResult.valid(
                SupportedProductPlatform.TEMU,
                url,
                temuUrlNormalizer.normalize(url),
                String.valueOf(goodsId)
        );
    }

    private QuickProductLinkValidationResult validateAmazon(String url) {
        String host = hostLower(url);
        if (host != null && (host.equals("amzn.to") || host.endsWith(".amzn.to"))) {
            return invalidForPlatform(
                    url,
                    SupportedProductPlatform.AMAZON,
                    "Skrócone linki amzn.to wymagają rozwinięcia przed walidacją — użyj pełnego URL produktu z ASIN."
            );
        }

        String path = pathLower(url);
        if (path == null || path.isBlank() || "/".equals(path)) {
            return invalidForPlatform(url, SupportedProductPlatform.AMAZON, "Link Amazon nie wskazuje na kartę produktu.");
        }
        if (path.startsWith("/s") || path.contains("/s/") || path.startsWith("/b") || path.contains("/b/")) {
            return invalidForPlatform(
                    url,
                    SupportedProductPlatform.AMAZON,
                    "Link Amazon wygląda na wyszukiwanie lub kategorię, a nie produkt."
            );
        }

        String asin = amazonUrlNormalizer.extractAsin(url);
        if (asin == null || asin.isBlank()) {
            return invalidForPlatform(
                    url,
                    SupportedProductPlatform.AMAZON,
                    "Link Amazon musi zawierać ASIN produktu (/dp/{ASIN} lub /gp/product/{ASIN})."
            );
        }

        return QuickProductLinkValidationResult.valid(
                SupportedProductPlatform.AMAZON,
                url,
                amazonUrlNormalizer.normalize(url),
                asin
        );
    }

    private SupportedProductPlatform detectPlatform(String url) {
        if (allegroUrlNormalizer.isAllegroUrl(url)) {
            return SupportedProductPlatform.ALLEGRO;
        }
        if (aliExpressUrlNormalizer.isAliExpressUrl(url)) {
            return SupportedProductPlatform.ALIEXPRESS;
        }
        if (temuUrlNormalizer.isTemuUrl(url)) {
            return SupportedProductPlatform.TEMU;
        }
        if (amazonUrlNormalizer.isAmazonUrl(url)) {
            return SupportedProductPlatform.AMAZON;
        }
        return SupportedProductPlatform.UNKNOWN;
    }

    private QuickProductLinkValidationResult invalidForPlatform(
            String url,
            SupportedProductPlatform platform,
            String reason
    ) {
        return QuickProductLinkValidationResult.invalid(url, platform, reason);
    }

    private URI parseUri(String url) {
        try {
            return URI.create(url);
        } catch (Exception e) {
            return null;
        }
    }

    private String pathLower(String url) {
        try {
            String path = URI.create(url.trim()).getPath();
            if (path == null || path.isBlank()) {
                return "/";
            }
            return path.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }

    private String hostLower(String url) {
        try {
            String host = URI.create(url.trim()).getHost();
            if (host == null) {
                return null;
            }
            return host.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }
}
