package by.moby.payment.transfers.api;

import by.moby.common.error.BusinessException;
import by.moby.common.error.ErrorCode;
import by.moby.common.events.TransferCompleted;
import by.moby.common.money.Money;
import by.moby.payment.account.Account;
import by.moby.payment.account.ChildAccounts;
import by.moby.payment.platform.web.IdempotencyKey;
import by.moby.payment.transfers.SavingsService;
import by.moby.payment.transfers.SavingsService.SavingsResult;
import by.moby.payment.transfers.TransferResult;
import by.moby.payment.transfers.TransferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Пополнение ребёнка родителем и внутренние переводы для family. Внутренние эндпоинты вызываются
 * с JWT пользователя (ретрансляция) — права проверяются так же, как для внешних.
 */
@RestController
@RequestMapping("/api/payment")
class TransferController {

    record TopUpRequest(String amount) {
    }

    record InternalTransferRequest(@NotBlank String childId, @NotNull Money amount, @NotNull TransferCompleted.Kind kind,
                                   @NotBlank String title) {
    }

    record OpenSavingsRequest(@NotBlank String childId, @NotBlank String piggyId) {
    }

    record SavingsDepositRequest(@NotNull Money amount, @NotBlank String title) {
    }

    record SavingsWithdrawRequest(@NotBlank String title) {
    }

    private final TransferService transfers;
    private final SavingsService savings;
    private final ChildAccounts accounts;

    TransferController(TransferService transfers, SavingsService savings, ChildAccounts accounts) {
        this.transfers = transfers;
        this.savings = savings;
        this.accounts = accounts;
    }

    /** {@code parent/topUp}: «Пополнение от мамы». */
    @PostMapping("/children/{childId}/topups")
    @ResponseStatus(HttpStatus.CREATED)
    TransferResult topUp(@PathVariable String childId, @RequestBody TopUpRequest request,
                         @RequestHeader(IdempotencyKey.HEADER) String idempotencyKey) {
        Account child = accounts.managedChild(childId);
        Money amount = Money.parse(request.amount()).filter(Money::isPositive)
                .orElseThrow(() -> new BusinessException(ErrorCode.AMOUNT, "Укажите сумму — не больше двух знаков после запятой"));
        String title = "Пополнение от " + accounts.parentOfFamily(child.getFamilyId()).getHolder().genitive();
        return transfers.parentToChild(accounts.currentUser().memberId(), idempotencyKey, child, amount, TransferCompleted.Kind.TOPUP, title);
    }

    /** family: одобренный запрос на пополнение, награда за задание. Только родитель семьи ребёнка. */
    @PostMapping("/internal/transfers/parent-to-child")
    @ResponseStatus(HttpStatus.CREATED)
    TransferResult parentToChild(@Valid @RequestBody InternalTransferRequest request,
                                 @RequestHeader(IdempotencyKey.HEADER) String idempotencyKey) {
        Account child = accounts.managedChild(request.childId());
        return transfers.parentToChild(accounts.currentUser().memberId(), idempotencyKey, child, request.amount(), request.kind(), request.title());
    }

    @PostMapping("/internal/savings")
    @ResponseStatus(HttpStatus.CREATED)
    SavingsResult openSavings(@Valid @RequestBody OpenSavingsRequest request) {
        return savings.open(request.childId(), request.piggyId());
    }

    @PostMapping("/internal/savings/{piggyId}/deposit")
    SavingsResult deposit(@PathVariable String piggyId, @Valid @RequestBody SavingsDepositRequest request,
                          @RequestHeader(IdempotencyKey.HEADER) String idempotencyKey) {
        return savings.deposit(piggyId, request.amount(), request.title(), idempotencyKey);
    }

    @PostMapping("/internal/savings/{piggyId}/withdraw")
    SavingsResult withdraw(@PathVariable String piggyId, @Valid @RequestBody SavingsWithdrawRequest request,
                           @RequestHeader(IdempotencyKey.HEADER) String idempotencyKey) {
        return savings.withdrawAll(piggyId, request.title(), idempotencyKey);
    }
}
