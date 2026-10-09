package com.parcelshare.common;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal, admin-facing log of sensitive actions. Separate from the user-visible tracking events. */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repo;

    @Transactional
    public void log(Long actorId, String action, String entityType, Long entityId, String detail) {
        repo.save(new AuditLog(actorId, action, entityType, entityId, detail));
    }

    @Transactional(readOnly = true)
    public List<AuditLog> latest(int limit) {
        return repo.findAll(PageRequest.of(0, Math.min(limit, 200), Sort.by(Sort.Direction.DESC, "id"))).getContent();
    }
}
