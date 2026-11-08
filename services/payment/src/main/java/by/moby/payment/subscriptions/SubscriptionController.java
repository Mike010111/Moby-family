package by.moby.payment.subscriptions;

import by.moby.common.money.Money;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment/children/{childId}/subscriptions/{id}")
class SubscriptionController {

    /** Частичное изменение: только переданные поля. */
    record SubscriptionPatch(Boolean active, String price) {
    }

    record SubscriptionView(String id, String title, Money price, String next, boolean active, String icon) {
    }

    private final SubscriptionService subscriptions;

    SubscriptionController(SubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @PatchMapping
    SubscriptionView update(@PathVariable String childId, @PathVariable String id, @RequestBody SubscriptionPatch patch) {
        Subscription s = subscriptions.update(childId, id, patch.active(), patch.price());
        return new SubscriptionView(s.getId(), s.getTitle(), s.getPrice(), s.getNextLabel(), s.isActive(), s.getIcon());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String childId, @PathVariable String id) {
        subscriptions.delete(childId, id);
    }
}
