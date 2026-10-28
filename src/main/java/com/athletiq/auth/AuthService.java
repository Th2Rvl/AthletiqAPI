package com.athletiq.auth;

import com.athletiq.auth.dto.LoginRequest;
import com.athletiq.auth.dto.LoginResponse;
import com.athletiq.auth.dto.RegisterAthleteRequest;
import com.athletiq.auth.dto.RegisterCoachRequest;
import com.athletiq.common.exception.InvalidCredentialsException;
import com.athletiq.common.security.JwtTokenService;
import com.athletiq.user.Role;
import com.athletiq.user.User;
import com.athletiq.user.UserMapper;
import com.athletiq.user.UserErrors;
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

    /**
     * Crée un compte coach (US06) et le connecte immédiatement.
     *
     * <p>Le rôle est fixé ici, jamais lu dans la requête : un compte créé depuis le web est un
     * compte coach (RM3). La robustesse du mot de passe est revérifiée côté service, l'annotation
     * du DTO n'étant qu'une première barrière.
     */
    @Transactional
    public LoginResponse register(RegisterCoachRequest request) {
        return createAccount(request.email(), request.password(), request.firstName(), request.lastName(),
                Role.COACH);
    }

    /**
     * Crée un compte athlète (#8, US01) et le connecte immédiatement. Même règle que pour le coach :
     * le rôle est fixé par l'API, jamais lu dans la requête (RM3). L'athlète n'a aucun coach à la
     * création : il devra saisir un code d'invitation pour utiliser l'application.
     */
    @Transactional
    public LoginResponse registerAthlete(RegisterAthleteRequest request) {
        return createAccount(request.email(), request.password(), request.firstName(), request.lastName(),
                Role.ATHLETE);
    }

    private LoginResponse createAccount(String email, String password, String firstName, String lastName,
                                        Role role) {
        if (!PasswordPolicy.isValid(password)) {
            throw AuthErrors.passwordRuleViolation();
        }
        if (userService.existsByEmail(email)) {
            throw UserErrors.emailAlreadyExists();
        }
        User created = userService.create(email, password, firstName, lastName, role);
        String token = jwtTokenService.createAccessToken(created.getId(), created.getRole());
        return LoginResponse.bearer(token, jwtTokenService.accessTokenTtlSeconds(),
                userMapper.toResponse(created));
    }
}
