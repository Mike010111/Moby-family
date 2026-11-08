package by.moby.family.requests;

import by.moby.common.error.BusinessException;
import by.moby.common.error.ErrorCode;
import by.moby.common.events.Actor;
import by.moby.common.events.RequestCreated;
import by.moby.common.events.RequestKind;
import by.moby.common.events.RequestResolved;
import by.moby.common.money.Money;
import by.moby.family.members.Member;
import by.moby.family.members.Members;
import by.moby.family.payment.PaymentClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Запросы ребёнка и их одобрение главным родственником ({@code request/*}, {@code p2p/requestOver}). */
@Service
public class RequestService {

    static final Money MAX_TOPUP_REQUEST = Money.of("500");

    private final ChildRequestRepository requests;
    private final Members members;
    private final PaymentClient payment;
    private final PiggyRequestHandler piggies;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RequestService(ChildRequestRepository requests, Members members, PaymentClient payment, PiggyRequestHandler piggies,
                          ApplicationEventPublisher events, Clock clock) {
        this.requests = requests;
        this.members = members;
        this.payment = payment;
        this.piggies = piggies;
        this.events = events;
        this.clock = clock;
    }

    public record Created(ChildRequest request, Member relative) {
    }

    /** {@code request/create}: просьба о пополнении. Не главному родственнику запрос «уходит» (SENT) и в хаб не попадает. */
    @Transactional
    public Created createTopUp(String childId, String relativeId, String rawAmount, String rawReason) {
        Member child = members.self(childId);
        members.requireNotFrozen(child);
        Money amount = Money.parse(rawAmount).filter(Money::isPositive)
                .orElseThrow(() -> new BusinessException(ErrorCode.AMOUNT, "Укажите сумму — не больше двух знаков после запятой"));
        if (amount.isGreaterThan(MAX_TOPUP_REQUEST)) {
            throw new BusinessException(ErrorCode.TOO_LARGE, "Слишком большая сумма для запроса");
        }
        Member relative = members.relativeOrMain(child.getFamilyId(), relativeId);
        String reason = rawReason == null || rawReason.isBlank() ? "Без причины" : truncate(rawReason.trim(), 60);
        RequestStatus status = relative.isMain() ? RequestStatus.PENDING : RequestStatus.SENT;
        ChildRequest request = requests.save(new ChildRequest(child.getFamilyId(), childId, relative.getId(), RequestKind.TOPUP,
                amount, reason, status, Instant.now(clock)));
        publishCreated(child, request, relative.isMain(), null, null, null, null);
        return new Created(request, relative);
    }

    /** {@code p2p/requestOver}: разовое одобрение перевода сверх лимита. Новый запрос заменяет прежний ожидающий. */
    @Transactional
    public Created createP2pOver(String childId, String contactId, String rawAmount) {
        Member child = members.self(childId);
        PaymentClient.Quote quote = payment.quoteP2p(childId, rawAmount, contactId);
        if (quote.contactName() == null) {
            throw new BusinessException(ErrorCode.VALIDATION, "Выберите получателя");
        }
        if ("BLOCKED".equals(quote.mode())) {
            throw new BusinessException(quote.code(), quote.reason());
        }
        if (!"REQUEST".equals(quote.mode())) {
            throw new BusinessException(ErrorCode.NOT_NEEDED, "Сумма укладывается в лимит — запрос не нужен");
        }
        Money amount = Money.parse(rawAmount).orElseThrow();
        if (amount.isGreaterThan(quote.balance())) {
            throw new BusinessException(ErrorCode.FUNDS, "Недостаточно средств");
        }
        Instant now = Instant.now(clock);
        requests.findByChildIdAndKindAndStatus(childId, RequestKind.P2P_OVER, RequestStatus.PENDING)
                .forEach(r -> r.cancel(now, "Заменён новым запросом"));
        Member main = members.mainRelative(child.getFamilyId());
        ChildRequest request = requests.save(new ChildRequest(child.getFamilyId(), childId, main.getId(), RequestKind.P2P_OVER, amount,
                "Перевод: " + quote.contactName(), RequestStatus.PENDING, now).withContact(quote.contactName()));
        publishCreated(child, request, true, quote.remaining(), null, null, null);
        return new Created(request, main);
    }

    /** Запрос на изменение / удаление копилки (7–13). Вызывается модулем piggies. */
    @Transactional
    public Created createPiggyRequest(Member child, String piggyId, String goal, Money saved, Money target, RequestKind kind, Money amount) {
        if (requests.existsByPiggyIdAndStatus(piggyId, RequestStatus.PENDING)) {
            throw new BusinessException(ErrorCode.PENDING, "По этой копилке уже ждём ответа");
        }
        Member main = members.mainRelative(child.getFamilyId());
        String reason = kind == RequestKind.PIGGY_DELETE
                ? "Удалить копилку «" + goal + "»"
                : "Изменить цель «" + goal + "»: " + target.format() + " → " + amount.format();
        ChildRequest request = requests.save(new ChildRequest(child.getFamilyId(), child.getId(), main.getId(), kind, amount, reason,
                RequestStatus.PENDING, Instant.now(clock)).withPiggy(piggyId));
        publishCreated(child, request, true, null, goal, saved, target);
        return new Created(request, main);
    }

    /** {@code request/approve}. Повторное одобрение — 409 ALREADY_PROCESSED. */
    @Transactional(noRollbackFor = RequestCancelledException.class)
    public ChildRequest approve(String requestId) {
        ChildRequest request = pending(requestId);
        Member child = members.child(request.getChildId());
        if (child.isFrozen()) {
            throw new BusinessException(ErrorCode.FROZEN, "Счёт " + child.getName() + " заблокирован — сначала разблокируйте");
        }
        Member main = members.mainRelative(request.getFamilyId());
        Instant now = Instant.now(clock);
        String piggyGoal = null;
        Money returned = null;
        switch (request.getKind()) {
            case TOPUP -> payment.parentToChild(child.getId(), request.getAmount(), PaymentClient.TransferKind.TOPUP,
                    main.getLabel() + " " + main.verb("одобрил") + ": " + request.getReason(), "request:" + request.getId());
            case P2P_OVER -> payment.grantP2pApproval(child.getId(), request.getId(), request.getAmount());
            case PIGGY_UPDATE, PIGGY_DELETE -> {
                Optional<PiggyRequestHandler.Outcome> outcome = request.getKind() == RequestKind.PIGGY_UPDATE
                        ? piggies.approveUpdate(request)
                        : piggies.approveDelete(request);
                if (outcome.isEmpty()) {
                    request.cancel(now, "Копилки уже нет");
                    throw new RequestCancelledException("Копилки уже нет — запрос отменён");
                }
                piggyGoal = outcome.get().goal();
                returned = outcome.get().returned();
            }
        }
        request.approve(now);
        events.publishEvent(new RequestResolved(UUID.randomUUID(), now, request.getFamilyId(), child.getId(), request.getId(),
                request.getKind(), true, request.getAmount(), request.getReason(), actor(main), piggyGoal, returned));
        return request;
    }

    /** {@code request/decline}. */
    @Transactional
    public ChildRequest decline(String requestId) {
        ChildRequest request = pending(requestId);
        Member main = members.mainRelative(request.getFamilyId());
        Instant now = Instant.now(clock);
        request.decline(now);
        String goal = request.getPiggyId() == null ? null : piggies.goalOf(request).orElse(null);
        events.publishEvent(new RequestResolved(UUID.randomUUID(), now, request.getFamilyId(), request.getChildId(), request.getId(),
                request.getKind(), false, request.getAmount(), request.getReason(), actor(main), goal, null));
        return request;
    }

    /** Запросы, которые видит пользователь: родитель — всей семьи, ребёнок — свои. */
    @Transactional(readOnly = true)
    public List<ChildRequest> visible() {
        var user = members.currentUser();
        return user.isParent()
                ? requests.findByFamilyIdOrderByCreatedAtDesc(user.familyId())
                : requests.findByChildIdOrderByCreatedAtDesc(user.memberId());
    }

    private ChildRequest pending(String requestId) {
        ChildRequest request = requests.lockById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Запрос не найден"));
        members.access().requireParentOf(request.getFamilyId());
        if (!request.isPending()) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED, "Запрос уже обработан");
        }
        return request;
    }

    private void publishCreated(Member child, ChildRequest r, boolean toMain, Money limitRemaining, String goal, Money saved, Money target) {
        events.publishEvent(new RequestCreated(UUID.randomUUID(), r.getCreatedAt(), r.getFamilyId(), child.getId(), child.getName(),
                r.getId(), r.getKind(), r.getRelativeId(), toMain, r.getAmount(), r.getReason(), r.getContactName(), limitRemaining,
                goal, saved, target));
    }

    private static Actor actor(Member relative) {
        return new Actor(relative.getId(), relative.getLabel(), relative.isFemale());
    }

    static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
