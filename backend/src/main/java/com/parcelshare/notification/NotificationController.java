package com.parcelshare.notification;

import com.parcelshare.identity.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    public NotificationService.Inbox inbox(@AuthenticationPrincipal AuthUser me) {
        return service.inbox(me.id());
    }

    @PostMapping("/read-all")
    public void readAll(@AuthenticationPrincipal AuthUser me) {
        service.markAllRead(me.id());
    }
}
