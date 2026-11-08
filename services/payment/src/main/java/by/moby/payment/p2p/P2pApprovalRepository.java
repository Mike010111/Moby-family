package by.moby.payment.p2p;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface P2pApprovalRepository extends JpaRepository<P2pApproval, String> {

    Optional<P2pApproval> findFirstByAccountIdAndStatus(String accountId, ApprovalStatus status);

    List<P2pApproval> findByAccountIdAndStatus(String accountId, ApprovalStatus status);

    Optional<P2pApproval> findByRequestId(String requestId);

    List<P2pApproval> findByAccountIdOrderByCreatedAtDesc(String accountId);
}
