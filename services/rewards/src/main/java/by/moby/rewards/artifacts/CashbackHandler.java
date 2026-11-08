package by.moby.rewards.artifacts;

import by.moby.common.events.CashbackGranted;
import by.moby.rewards.platform.messaging.EventHandler;
import org.springframework.stereotype.Component;

@Component
class CashbackHandler implements EventHandler<CashbackGranted> {

    private final ArtifactService artifacts;

    CashbackHandler(ArtifactService artifacts) {
        this.artifacts = artifacts;
    }

    @Override
    public Class<CashbackGranted> type() {
        return CashbackGranted.class;
    }

    @Override
    public void handle(CashbackGranted event) {
        artifacts.recordCashback(event.childId(), event.artifactId(), event.amount());
    }
}
