package by.moby.payment.overview;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payment")
class OverviewController {

    private final AccountOverviewService overview;

    OverviewController(AccountOverviewService overview) {
        this.overview = overview;
    }

    @GetMapping("/children/{childId}/account")
    ChildAccountView account(@PathVariable String childId) {
        return overview.account(childId);
    }

    /** Выписка (последние операции, новые сверху) — {@code child.tx} прототипа. */
    @GetMapping("/children/{childId}/operations")
    List<OperationView> operations(@PathVariable String childId, @RequestParam(defaultValue = "60") @Min(1) @Max(200) int limit) {
        return overview.operations(childId, limit);
    }

    @GetMapping("/me/parent-account")
    ParentAccountView parentAccount() {
        return overview.parentAccount();
    }
}
