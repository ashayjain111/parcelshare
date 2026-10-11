package com.parcelshare.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.parcelshare.common.AppProps;
import com.parcelshare.identity.AppUser;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {

    private final NotificationRepository repo = mock(NotificationRepository.class);
    private final MailService mail = mock(MailService.class);
    private final AppProps props = new AppProps("x".repeat(40), 12, 10, 10, 48, 24, 30, false, List.of(), "a@b.c", "", false, false,
            "", "https://app.example", false, 180, "", "", "https://x/authorize", "https://x/token", "https://x/user");
    private final NotificationService service = new NotificationService(repo, mail, props);

    private AppUser user(AppUser.Status status) {
        AppUser u = new AppUser();
        u.setId(7L);
        u.setEmail("person@example.com");
        u.setFullName("Real Person");
        u.setStatus(status);
        return u;
    }

    @Test
    void activeUsersGetAnInAppNotificationAndAnEmail() {
        service.notify(user(AppUser.Status.ACTIVE), "BOOKING_ACCEPTED", "Accepted", "Your booking was accepted", "/bookings/1");
        verify(repo).save(any(Notification.class));
        verify(mail).send(anyString(), anyString(), anyString());
    }

    @Test
    void deletedAccountsAreNeverNotifiedOrEmailed() {
        service.notify(user(AppUser.Status.DELETED), "BOOKING_ACCEPTED", "Accepted", "Your booking was accepted", "/bookings/1");
        verify(repo, never()).save(any(Notification.class));
        verify(mail, never()).send(anyString(), anyString(), anyString());
    }
}
