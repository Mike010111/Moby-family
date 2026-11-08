package by.moby.payment.limits.api;

import by.moby.payment.limits.LimitService;
import by.moby.payment.limits.LimitValidator;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment/children/{childId}/limits")
class LimitController {

    private final LimitService limits;
    private final LimitViewMapper mapper;

    LimitController(LimitService limits, LimitViewMapper mapper) {
        this.limits = limits;
        this.mapper = mapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    LimitView create(@PathVariable String childId, @RequestBody LimitRequest request) {
        return mapper.toView(limits.create(childId, input(request)));
    }

    @PutMapping("/{limitId}")
    LimitView update(@PathVariable String childId, @PathVariable String limitId, @RequestBody LimitRequest request) {
        return mapper.toView(limits.update(childId, limitId, input(request)));
    }

    @DeleteMapping("/{limitId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String childId, @PathVariable String limitId) {
        limits.delete(childId, limitId);
    }

    private static LimitValidator.Input input(LimitRequest r) {
        return new LimitValidator.Input(r.count(), r.unit(), r.amount(), r.categories());
    }
}
