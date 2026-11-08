package by.moby.payment.payments;

import by.moby.common.error.BusinessException;
import by.moby.payment.platform.security.AccessPolicy;
import by.moby.payment.platform.web.IdempotencyKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment/children/{childId}/qr-payments")
class QrPaymentController {

    record QrPaymentRequest(@NotBlank String merchantId) {
    }

    private final QrPaymentService payments;
    private final AccessPolicy access;

    QrPaymentController(QrPaymentService payments, AccessPolicy access) {
        this.payments = payments;
        this.access = access;
    }

    /** Оплачивает только сам ребёнок. Отказ — 422 ProblemDetail с кодом из checkPayment и полем merchant. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    QrPaymentResult pay(@PathVariable String childId, @Valid @RequestBody QrPaymentRequest request,
                        @RequestHeader(IdempotencyKey.HEADER) String idempotencyKey) {
        access.requireSelf(childId);
        QrPaymentResult result = payments.pay(childId, request.merchantId(), idempotencyKey);
        if (result.status() == QrPaymentResult.Status.DECLINED) {
            throw new BusinessException(result.code(), result.reason())
                    .with("merchant", result.merchant())
                    .with("operationId", result.operationId());
        }
        return result;
    }
}
