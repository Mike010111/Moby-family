package by.moby.family.piggies;

import by.moby.common.money.Money;
import by.moby.family.platform.web.IdempotencyKey;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/family/children/{childId}/piggies")
class PiggyController {

    record NewPiggy(String goal, String target) {
    }

    record AmountBody(String amount) {
    }

    record TargetBody(String target) {
    }

    record PiggyView(String id, String goal, Money target, Money saved, String status) {
        static PiggyView of(Piggy p) {
            return new PiggyView(p.getId(), p.getGoal(), p.getTarget(), p.getSaved(), p.getStatus().name());
        }
    }

    /** {@code pending} — ушёл запрос главному родственнику (7–13); {@code completed} — копилка собрана. */
    record ResultView(PiggyView piggy, PiggyFunds.Completed completed, Money returned, boolean pending, String requestId,
                      String relativeLabel, String relativeDative) {
        static ResultView of(PiggyService.Result r) {
            return new ResultView(PiggyView.of(r.piggy()), r.completed(), r.returned(), r.pending() != null,
                    r.pending() == null ? null : r.pending().request().getId(),
                    r.pending() == null ? null : r.pending().relative().getLabel(),
                    r.pending() == null ? null : r.pending().relative().getDative());
        }
    }

    private final PiggyService piggies;

    PiggyController(PiggyService piggies) {
        this.piggies = piggies;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PiggyView create(@PathVariable String childId, @RequestBody NewPiggy body) {
        return PiggyView.of(piggies.create(childId, body.goal(), body.target()));
    }

    @PostMapping("/{piggyId}/deposits")
    ResultView deposit(@PathVariable String childId, @PathVariable String piggyId, @RequestBody AmountBody body,
                       @RequestHeader(IdempotencyKey.HEADER) String idempotencyKey) {
        return ResultView.of(piggies.deposit(childId, piggyId, body.amount(), idempotencyKey));
    }

    @PutMapping("/{piggyId}/target")
    ResultView updateTarget(@PathVariable String childId, @PathVariable String piggyId, @RequestBody TargetBody body) {
        return ResultView.of(piggies.updateTarget(childId, piggyId, body.target()));
    }

    @DeleteMapping("/{piggyId}")
    ResultView delete(@PathVariable String childId, @PathVariable String piggyId) {
        return ResultView.of(piggies.delete(childId, piggyId));
    }
}
