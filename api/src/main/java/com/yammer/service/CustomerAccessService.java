package com.yammer.service;

import com.yammer.entity.CustomerSessionEntity;
import com.yammer.entity.TableSessionEntity;
import com.yammer.repository.CustomerSessionRepository;
import com.yammer.repository.TableSessionRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Customer access to a table: joining an open table session (PENDING until the
 * assigned waiter approves) and validating the browser-stored token on later
 * requests. A token from a CLOSED table session is worthless — closing the table
 * invalidates every customer session with it.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CustomerAccessService {

    public static final String NONE = "NONE";

    private final TableSessionRepository sessionRepository;
    private final CustomerSessionRepository customerSessionRepository;

    /** The token's status at this point's CURRENT open session: NONE / PENDING / APPROVED / DENIED. */
    @Transactional(readOnly = true)
    public String statusAt(UUID orderPointId, UUID token) {
        return currentSession(orderPointId, token)
                .map(CustomerSessionEntity::getStatus)
                .orElse(NONE);
    }

    /**
     * Join (or resume) the point's open table session. A valid token for the current
     * session is resumed as-is (approval survives tab/browser closes); anything else
     * creates a fresh PENDING request for the assigned waiter to approve.
     */
    public CustomerSessionEntity join(UUID orderPointId, UUID token) {
        TableSessionEntity open = sessionRepository.findByOrderPointIdAndClosedAtIsNull(orderPointId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Table is not open"));
        if (token != null) {
            Optional<CustomerSessionEntity> existing = customerSessionRepository.findByToken(token)
                    .filter(cs -> cs.getTableSessionId().equals(open.getId()));
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        CustomerSessionEntity cs = new CustomerSessionEntity();
        cs.setTableSessionId(open.getId());
        return customerSessionRepository.save(cs);
    }

    /** The APPROVED customer session behind the token, or 403. */
    @Transactional(readOnly = true)
    public CustomerSessionEntity requireApproved(UUID orderPointId, UUID token) {
        return currentSession(orderPointId, token)
                .filter(cs -> CustomerSessionEntity.APPROVED.equals(cs.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Not approved at this table"));
    }

    private Optional<CustomerSessionEntity> currentSession(UUID orderPointId, UUID token) {
        if (token == null) {
            return Optional.empty();
        }
        return sessionRepository.findByOrderPointIdAndClosedAtIsNull(orderPointId)
                .flatMap(open -> customerSessionRepository.findByToken(token)
                        .filter(cs -> cs.getTableSessionId().equals(open.getId())));
    }
}
