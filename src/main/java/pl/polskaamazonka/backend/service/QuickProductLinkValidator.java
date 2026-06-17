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
    private final AffiliateShortLinkResolver affiliateShortLinkResolver;

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

        String originalUrl = trimmedUrl;
        String urlToValidate = trimmedUrl;
        ShortLinkResolution shortLinkResolution = affiliateShortLinkResolver.resolve(trimmedUrl);
        if (shortLinkResolution.status() == ShortLinkResolution.Status.FAILURE) {
            return QuickProductLinkValidationResult.invalid(
                    originalUrl,
                    detectPlatform(originalUrl),
                    shortLinkResolution.failureReason()
            );
        }
        if (shortLinkResolution.status() == ShortLinkResolution.Status.SUCCESS) {
            urlToValidate = shortLinkResolution.expandedUrl();
        }

        SupportedProductPlatform platform = detectPlatform(urlToValidate);
        if (platform == SupportedProductPlatform.UNKNOWN) {
            return QuickProductLinkValidationResult.unsupported(
                    originalUrl,
                    "Domena nie należy do obsługiwanych platform sklepowych."
            );
        }

        return switch (platform) {
            case ALLEGRO -> validateAllegro(urlToValidate, originalUrl);
            case ALIEXPRESS -> validateAliExpress(urlToValidate, originalUrl);
            case TEMU -> validateTemu(urlToValidate, originalUrl);
            case AMAZON -> validateAmazon(urlToValidate, originalUrl);
            default -> QuickProductLinkValidationResult.unsupported(
                    originalUrl,
                    "Domena nie należy do obsługiwanych platform sklepowych."
            );
        };
    }

    private QuickProductLinkValidationResult validateAllegro(String url, String originalUrl) {
        String path = pathLower(url);
        if (path == null || path.isBlank() || "/".equals(path)) {
            return invalidForPlatform(originalUrl, SupportedProductPlatform.ALLEGRO, "Link Allegro nie wskazuje na kartę oferty.");
        }
        if (!path.contains("/oferta/")) {
            return invalidForPlatform(originalUrl, SupportedProductPlatform.ALLEGRO, "Link Allegro musi prowadzić do oferty (/oferta/).");
        }
        if (path.contains("/listing") || path.contains("/kategoria/")) {
            return invalidForPlatform(originalUrl, SupportedProductPlatform.ALLEGRO, "Link Allegro wygląda na wyszukiwanie lub kategorię, a nie ofertę.");
        }

        Long offerId = allegroUrlNormalizer.extractOfferId(url);
        if (offerId == null) {
            return invalidForPlatform(originalUrl, SupportedProductPlatform.ALLEGRO, "Nie udało się odczytać ID oferty Allegro z adresu URL.");
        }

        return QuickProductLinkValidationResult.valid(
                SupportedProductPlatform.ALLEGRO,
                originalUrl,
                allegroUrlNormalizer.normalize(url),
                String.valueOf(offerId)
        );
    }

    private QuickProductLinkValidationResult validateAliExpress(String url, String originalUrl) {
        String path = pathLower(url);
        if (path == null || path.isBlank() || "/".equals(path)) {
            return invalidForPlatform(originalUrl, SupportedProductPlatform.ALIEXPRESS, "Link AliExpress nie wskazuje na kartę produktu.");
        }
        if (path.contains("/w/") || path.contains("/category/") || path.contains("/wholesale")) {
            return invalidForPlatform(
                    originalUrl,
                    SupportedProductPlatform.ALIEXPRESS,
                    "Link AliExpress wygląda na wyszukiwanie lub kategorię, a nie produkt."
            );
        }

        Long itemId = aliExpressUrlNormalizer.extractItemId(url);
        if (itemId == null) {
            return invalidForPlatform(
                    originalUrl,
                    SupportedProductPlatform.ALIEXPRESS,
                    "Link AliExpress musi zawierać /item/{id}.html."
            );
        }

        return QuickProductLinkValidationResult.valid(
                SupportedProductPlatform.ALIEXPRESS,
                originalUrl,
                aliExpressUrlNormalizer.normalize(url),
                String.valueOf(itemId)
        );
    }

    private QuickProductLinkValidationResult validateTemu(String url, String originalUrl) {
        String path = pathLower(url);
        if (path == null || path.isBlank() || "/".equals(path)) {
            return invalidForPlatform(originalUrl, SupportedProductPlatform.TEMU, "Link Temu nie wskazuje na kartę produktu.");
        }
        if (path.contains("/category") || path.endsWith("/category.html")) {
            return invalidForPlatform(originalUrl, SupportedProductPlatform.TEMU, "Link Temu wygląda na kategorię, a nie produkt.");
        }
        if (path.contains("search_result") && temuUrlNormalizer.extractGoodsId(url) == null) {
            return invalidForPlatform(
                    originalUrl,
                    SupportedProductPlatform.TEMU,
                    "Link Temu search_result wymaga parametru goods_id."
            );
        }

        Long goodsId = temuUrlNormalizer.extractGoodsId(url);
        if (goodsId == null) {
            return invalidForPlatform(
                    originalUrl,
                    SupportedProductPlatform.TEMU,
                    "Link Temu musi zawierać ID produktu (-g-{id} lub goods_id)."
            );
        }

        return QuickProductLinkValidationResult.valid(
                SupportedProductPlatform.TEMU,
                originalUrl,
                temuUrlNormalizer.normalize(url),
                String.valueOf(goodsId)
        );
    }

    private QuickProductLinkValidationResult validateAmazon(String url, String originalUrl) {
        String host = hostLower(url);
        if (host != null && (host.equals("amzn.to") || host.endsWith(".amzn.to"))) {
            return invalidForPlatform(
                    originalUrl,
                    SupportedProductPlatform.AMAZON,
                    "Skrócone linki amzn.to wymagają rozwinięcia przed walidacją — użyj pełnego URL produktu z ASIN."
            );
        }

        String path = pathLower(url);
        if (path == null || path.isBlank() || "/".equals(path)) {
            return invalidForPlatform(originalUrl, SupportedProductPlatform.AMAZON, "Link Amazon nie wskazuje na kartę produktu.");
        }
        if (path.startsWith("/s") || path.contains("/s/") || path.startsWith("/b") || path.contains("/b/")) {
            return invalidForPlatform(
                    originalUrl,
                    SupportedProductPlatform.AMAZON,
                    "Link Amazon wygląda na wyszukiwanie lub kategorię, a nie produkt."
            );
        }

        String asin = amazonUrlNormalizer.extractAsin(url);
        if (asin == null || asin.isBlank()) {
            return invalidForPlatform(
                    originalUrl,
                    SupportedProductPlatform.AMAZON,
                    "Link Amazon musi zawierać ASIN produktu (/dp/{ASIN} lub /gp/product/{ASIN})."
            );
        }

        return QuickProductLinkValidationResult.valid(
                SupportedProductPlatform.AMAZON,
                originalUrl,
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
