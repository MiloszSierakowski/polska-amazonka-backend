package pl.polskaamazonka.backend.service;

public interface ShortLinkRedirectClient {
    String expand(String url);
}
