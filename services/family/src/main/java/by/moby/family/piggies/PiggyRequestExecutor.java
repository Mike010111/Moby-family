package by.moby.family.piggies;

import by.moby.common.error.BusinessException;
import by.moby.common.error.ErrorCode;
import by.moby.family.members.Members;
import by.moby.family.requests.ChildRequest;
import by.moby.family.requests.PiggyRequestHandler;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Исполняет одобренные главным родственником запросы по копилкам 7–13. */
@Component
class PiggyRequestExecutor implements PiggyRequestHandler {

    private final PiggyRepository piggies;
    private final PiggyFunds funds;
    private final Members members;

    PiggyRequestExecutor(PiggyRepository piggies, PiggyFunds funds, Members members) {
        this.piggies = piggies;
        this.funds = funds;
        this.members = members;
    }

    @Override
    public Optional<Outcome> approveUpdate(ChildRequest request) {
        return piggies.lockActive(request.getPiggyId()).map(piggy -> {
            if (request.getAmount().isLessThan(piggy.getSaved())) {
                throw new BusinessException(ErrorCode.BELOW_SAVED, "Уже собрано " + piggy.getSaved().format() + " — это больше новой цели");
            }
            piggy.changeTarget(request.getAmount());
            if (piggy.isReached()) {
                funds.complete(members.child(piggy.getChildId()), piggy);
            }
            return new Outcome(piggy.getGoal(), null);
        });
    }

    @Override
    public Optional<Outcome> approveDelete(ChildRequest request) {
        return piggies.lockActive(request.getPiggyId()).map(piggy -> new Outcome(piggy.getGoal(), funds.closeAndReturn(piggy)));
    }

    @Override
    public Optional<String> goalOf(ChildRequest request) {
        return piggies.findById(request.getPiggyId()).map(Piggy::getGoal);
    }
}
