package com.athletiq.auth;

import com.athletiq.auth.dto.LoginRequest;
import com.athletiq.auth.dto.LoginResponse;
import com.athletiq.common.exception.InvalidCredentialsException;
import com.athletiq.common.security.JwtTokenService;
import com.athletiq.user.User;
import com.athletiq.user.UserMapper;
import com.athletiq.user.UserService;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final UserMapper userMapper;

    /**
     * Hash BCrypt d'un mot de passe quelconque. Il sert à faire un {@code matches} même quand l'e-mail
     * est inconnu : le temps de réponse est alors le même et ne révèle pas si le compte existe.
     */
    private final String dummyPasswordHash;

    public AuthService(UserService userService, PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService, UserMapper userMapper) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.userMapper = userMapper;
        this.dummyPasswordHash = passwordEncoder.encode("athletiq-timing-equalizer");
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Optional<User> user = userService.findByEmail(request.email());
        if (user.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(request.password(), user.get().getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        User authenticated = user.get();
        String token = jwtTokenService.createAccessToken(authenticated.getId(), authenticated.getRole());
        return LoginResponse.bearer(token, jwtTokenService.accessTokenTtlSeconds(),
                userMapper.toResponse(authenticated));
    }
}
