package by.moby.payment.demo;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** «Сбросить демо»: только профиль demo. */
@RestController
@Profile("demo")
class DemoController {

    private final PaymentDemoData demo;

    DemoController(PaymentDemoData demo) {
        this.demo = demo;
    }

    @EventListener(ApplicationReadyEvent.class)
    void seedOnStartup() {
        demo.seedIfEmpty();
    }

    @PostMapping("/api/payment/demo/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reset() {
        demo.reset();
    }
}
