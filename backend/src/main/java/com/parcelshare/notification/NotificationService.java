package com.parcelshare.notification;

import com.parcelshare.common.AppProps;
import com.parcelshare.identity.AppUser;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One place to tell a user something: saves the in-app notification and sends the email. */
@Service
@RequiredArgsConstructor
public class NotificationService {

    public record NotificationView(Long id, String type, String title, String body, String link, boolean read,
                                   Instant createdAt) {
    }

    public record Inbox(long unread, List<NotificationView> items) {
    }

    private final NotificationRepository repo;
    private final MailService mail;
    private final AppProps props;

    @Transactional
    public void notify(AppUser to, String type, String title, String line, String path) {
        if (to.getStatus() == AppUser.Status.DELETED) {
            return; // the account was anonymised: its email address is a placeholder and the person asked us to forget them
        }
        repo.save(new Notification(to.getId(), type, title, line, path));
        if (to.getEmail() != null) {
            String first = to.getFullName() == null || to.getFullName().isBlank() ? "there" : to.getFullName().trim().split(" ")[0];
            mail.send(to.getEmail(), title, "Hi " + first + ",\n\n" + line + "\n\nOpen it: " + props.frontendUrl() + path
                    + "\n\n-- ParcelShare\nYou get these emails because you have activity on ParcelShare.");
        }
    }

    @Transactional(readOnly = true)
    public Inbox inbox(Long userId) {
        List<NotificationView> items = repo.findTop30ByUserIdOrderByIdDesc(userId).stream()
                .map(n -> new NotificationView(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getLink(),
                        n.getReadAt() != null, n.getCreatedAt())).toList();
        return new Inbox(repo.countByUserIdAndReadAtIsNull(userId), items);
    }

    @Transactional
    public void markAllRead(Long userId) {
        repo.markAllRead(userId, Instant.now());
    }
}
