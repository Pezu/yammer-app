package com.yammer.service;

import com.yammer.repository.PaymentRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marks fiscal receipts FAILED when their {@code RECEIPT_RESULT} never arrives (half-open
 * socket, bridge crash mid-print, result lost on the way back). A send that "succeeds"
 * proves nothing; the only trustworthy signal is the result within a deadline. The bridge's
 * per-job budget (90 s) is strictly below this deadline (180 s), so a payment failed here is
 * provably no longer printing and a manual re-issue can never overlap a live job. The
 * bridge's persistent de-dup store makes a re-issue of an actually-printed receipt safe.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FiscalResultSweeper {

    private static final long SWEEP_INTERVAL_MS = 30_000;

    private final PaymentRepository paymentRepository;

    @Value("${bridge.fiscal-result-timeout-seconds:180}")
    private long resultTimeoutSeconds;

    @Scheduled(initialDelay = SWEEP_INTERVAL_MS, fixedDelay = SWEEP_INTERVAL_MS)
    @Transactional
    public void failStalePending() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(resultTimeoutSeconds);
        int failed = paymentRepository.failStaleFiscalPending(cutoff);
        if (failed > 0) {
            log.warn("Fiscal sweeper: {} payment(s) got no RECEIPT_RESULT within {}s — marked FAILED for manual re-issue.",
                    failed, resultTimeoutSeconds);
        }
    }
}
