package pl.polskaamazonka.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import pl.polskaamazonka.backend.security.SameSiteCookieCsrfTokenRepository;

@Configuration
public class CsrfConfig {

    @Bean
    public CsrfTokenRepository csrfTokenRepository(
            @Value("${app.auth.cookie.secure:false}") boolean secure
    ) {
        return new SameSiteCookieCsrfTokenRepository(secure);
    }

    @Bean
    public CsrfTokenRequestHandler csrfTokenRequestHandler() {
        return new CsrfTokenRequestAttributeHandler();
    }
}
