package com.yammer.service;

import com.yammer.entity.CustomerSessionEntity;
import com.yammer.repository.CustomerSessionRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Customer access to a table: joining the table's current round (PENDING until the
 * assigned waiter approves) and validating the browser-stored token on later
 * requests. A token from an earlier round is worthless — freeing the table (the last
 * waiter un-assigning) ends the round and with it every customer token issued in it.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CustomerAccessService {

    public static final String NONE = "NONE";

    private final TableRoundService roundService;
    private final CustomerSessionRepository customerSessionRepository;

    /** The token's status in this table's CURRENT round: NONE / PENDING / APPROVED / DENIED. */
    @Transactional(readOnly = true)
    public String statusAt(UUID orderPointId, UUID token) {
        return currentSession(orderPointId, token)
                .map(CustomerSessionEntity::getStatus)
                .orElse(NONE);
    }

    /**
     * Join (or resume) the table's current round. A token issued in this round is resumed
     * as-is (approval survives tab and browser closes); anything else creates a fresh
     * PENDING request for the assigned waiter to approve.
     */
    public CustomerSessionEntity join(UUID orderPointId, UUID token) {
        if (!roundService.inService(orderPointId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Table is not open");
        }
        Optional<CustomerSessionEntity> existing = currentSession(orderPointId, token);
        if (existing.isPresent()) {
            return existing.get();
        }
        CustomerSessionEntity cs = new CustomerSessionEntity();
        cs.setOrderPointId(orderPointId);
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

    /**
     * The token's row, if it belongs to this table and to the round running now. A row from
     * an earlier party is ignored: its createdAt predates the current round's start.
     */
    private Optional<CustomerSessionEntity> currentSession(UUID orderPointId, UUID token) {
        if (token == null) {
            return Optional.empty();
        }
        Optional<LocalDateTime> roundStart = roundService.roundStart(orderPointId);
        if (roundStart.isEmpty()) {
            return Optional.empty();
        }
        return customerSessionRepository.findByToken(token)
                .filter(cs -> orderPointId.equals(cs.getOrderPointId()))
                .filter(cs -> cs.getCreatedAt() == null || !cs.getCreatedAt().isBefore(roundStart.get()));
    }
}
