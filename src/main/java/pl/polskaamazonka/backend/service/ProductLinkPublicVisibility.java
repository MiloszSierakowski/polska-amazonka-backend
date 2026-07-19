package pl.polskaamazonka.backend.service;

import pl.polskaamazonka.backend.model.Link;

public final class ProductLinkPublicVisibility {

    private ProductLinkPublicVisibility() {
    }

    public static boolean isPubliclyAvailable(Link link) {
        return link != null
                && link.getUrl() != null
                && !link.getUrl().isBlank()
                && !Boolean.TRUE.equals(link.getIsBroken())
                && !Boolean.FALSE.equals(link.getIsActive());
    }
}
