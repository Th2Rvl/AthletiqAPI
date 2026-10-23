package com.athletiq.auth;

import static com.athletiq.support.TestEntities.user;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.athletiq.user.Role;
import com.athletiq.user.UserService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock
    private UserService userService;

    private AdminBootstrap bootstrap(String email, String password) {
        return new AdminBootstrap(new AdminProperties(email, password), userService);
    }

    @Test
    void doesNothingWhenNotConfigured() {
        bootstrap("", "").run(null);
        bootstrap("admin@athletiq.fr", null).run(null);

        verifyNoInteractions(userService);
    }

    @Test
    void refusesToStartWithATooShortPassword() {
        assertThatThrownBy(() -> bootstrap("admin@athletiq.fr", "court").run(null))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(userService);
    }

    @Test
    void createsTheAdminWhenAbsent() {
        when(userService.findByEmail("admin@athletiq.fr")).thenReturn(Optional.empty());

        bootstrap("admin@athletiq.fr", "mot-de-passe-solide").run(null);

        verify(userService).create("admin@athletiq.fr", "mot-de-passe-solide", "Admin", "Athletiq", Role.ADMIN);
    }

    @Test
    void neverOverwritesAnExistingAccount() {
        when(userService.findByEmail("admin@athletiq.fr"))
                .thenReturn(Optional.of(user("admin@athletiq.fr", Role.COACH)));

        bootstrap("admin@athletiq.fr", "mot-de-passe-solide").run(null);

        verify(userService, never()).create(anyString(), anyString(), anyString(), anyString(), any());
    }
}
