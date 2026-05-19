package pl.polskaamazonka.backend.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class LegacyAwarePasswordEncoder implements PasswordEncoder {

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    @Override
    public String encode(CharSequence rawPassword) {
        return bcrypt.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword != null && encodedPassword.startsWith("$2a$")) {
            return bcrypt.matches(rawPassword, encodedPassword);
        }
        return encodedPassword != null && encodedPassword.contentEquals(rawPassword);
    }
}
