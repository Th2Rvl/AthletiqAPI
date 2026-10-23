package com.athletiq.profile;

import static com.athletiq.support.TestEntities.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.athletiq.common.exception.BusinessRuleException;
import com.athletiq.common.security.AccessGuard;
import com.athletiq.common.security.CurrentUser;
import com.athletiq.profile.dto.CoachProfileResponse;
import com.athletiq.profile.dto.UpdateProfileRequest;
import com.athletiq.user.Role;
import com.athletiq.user.User;
import com.athletiq.user.UserErrors;
import com.athletiq.user.UserService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private UserService userService;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private AccessGuard accessGuard;

    private ProfileService service;

    @BeforeEach
    void setUp() {
        service = new ProfileService(userService, currentUser, accessGuard);
    }

    @Test
    void returnsTheProfileOfTheConnectedCoach() {
        User coach = user("claire.martin@club.fr", Role.COACH);
        when(currentUser.id()).thenReturn(USER_ID);
        when(userService.findById(USER_ID)).thenReturn(Optional.of(coach));

        CoachProfileResponse response = service.get();

        assertThat(response.email()).isEqualTo("claire.martin@club.fr");
        assertThat(response.role()).isEqualTo(Role.COACH);
        assertThat(response.id()).isEqualTo(coach.getId());
    }

    @Test
    void updatesOnlyTheProfileOfTheConnectedCoach() {
        User coach = user("claire.martin@club.fr", Role.COACH);
        when(currentUser.id()).thenReturn(USER_ID);
        when(userService.findById(USER_ID)).thenReturn(Optional.of(coach));
        when(userService.updateProfile(eq(USER_ID), any(), any(), any())).thenReturn(coach);

        service.update(new UpdateProfileRequest("claire.martin@stade-rennais-athle.fr", "Claire", "Martin-Ndiaye"));

        verify(accessGuard).assertSelf(USER_ID);
        verify(userService).updateProfile(USER_ID, "claire.martin@stade-rennais-athle.fr", "Claire", "Martin-Ndiaye");
    }

    @Test
    void refusesAnEmailAlreadyUsedByAnotherAccount() {
        User coach = user("claire.martin@club.fr", Role.COACH);
        when(currentUser.id()).thenReturn(USER_ID);
        when(userService.findById(USER_ID)).thenReturn(Optional.of(coach));
        when(userService.updateProfile(USER_ID, "autre@club.fr", "Claire", "Martin"))
                .thenThrow(UserErrors.emailAlreadyExists());

        assertThatThrownBy(() -> service.update(
                new UpdateProfileRequest("autre@club.fr", "Claire", "Martin")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("existe déjà");
    }

    @Test
    void doesNotTouchTheRoleOfTheCoach() {
        User coach = user("claire.martin@club.fr", Role.COACH);
        when(currentUser.id()).thenReturn(USER_ID);
        when(userService.findById(USER_ID)).thenReturn(Optional.of(coach));
        when(userService.updateProfile(eq(USER_ID), any(), any(), any())).thenReturn(coach);

        CoachProfileResponse response = service.update(new UpdateProfileRequest(null, "Claire", "Martin"));

        assertThat(response.role()).isEqualTo(Role.COACH);
        verify(userService).updateProfile(USER_ID, null, "Claire", "Martin");
        verify(userService, never()).updatePassword(any(), anyString());
    }
}
