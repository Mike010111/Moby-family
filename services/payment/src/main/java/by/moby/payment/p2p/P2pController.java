package by.moby.payment.p2p;

import by.moby.common.money.Money;
import by.moby.payment.platform.security.AccessPolicy;
import by.moby.payment.platform.web.IdempotencyKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment")
class P2pController {

    record SendRequest(@NotBlank String contactId, String amount) {
    }

    record ApprovalRequest(@NotBlank String requestId, @NotNull Money max) {
    }

    record ApprovalView(String id, String requestId, Money max, ApprovalStatus status) {
    }

    private final P2pService p2p;
    private final AccessPolicy access;

    P2pController(P2pService p2p, AccessPolicy access) {
        this.p2p = p2p;
        this.access = access;
    }

    @GetMapping("/children/{childId}/p2p/quote")
    P2pQuote quote(@PathVariable String childId, @RequestParam(required = false) String amount,
                   @RequestParam(required = false) String contactId) {
        return p2p.quote(childId, amount, contactId);
    }

    /** {@code p2p/send}: только сам подросток. REQUEST → 422 NEEDS_APPROVAL, BLOCKED → код причины. */
    @PostMapping("/children/{childId}/p2p-transfers")
    @ResponseStatus(HttpStatus.CREATED)
    P2pService.SendResult send(@PathVariable String childId, @Valid @RequestBody SendRequest request,
                               @RequestHeader(IdempotencyKey.HEADER) String idempotencyKey) {
        access.requireSelf(childId);
        return p2p.send(childId, request.contactId(), request.amount(), idempotencyKey);
    }

    /** family: родитель одобрил запрос p2p-over. */
    @PostMapping("/internal/children/{childId}/p2p-approvals")
    @ResponseStatus(HttpStatus.CREATED)
    ApprovalView grant(@PathVariable String childId, @Valid @RequestBody ApprovalRequest request) {
        P2pApproval a = p2p.grant(childId, request.requestId(), request.max());
        return new ApprovalView(a.getId(), a.getRequestId(), a.getMaxAmount(), a.getStatus());
    }
}
