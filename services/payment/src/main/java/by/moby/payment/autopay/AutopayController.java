package by.moby.payment.autopay;

import by.moby.common.money.Money;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment/children/{childId}/autopay")
class AutopayController {

    record AutopayView(boolean enabled, Money amount, String every, int weekday, int monthDay) {
        static AutopayView of(AutopayRule r) {
            return new AutopayView(r.isEnabled(), r.getAmount(), r.getEvery().key(), r.getWeekday(), r.getMonthDay());
        }
    }

    private final AutopayService autopay;

    AutopayController(AutopayService autopay) {
        this.autopay = autopay;
    }

    @PutMapping
    AutopayView update(@PathVariable String childId, @RequestBody AutopayService.AutopayInput input) {
        return AutopayView.of(autopay.update(childId, input));
    }
}
