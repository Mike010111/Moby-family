package by.moby.family.requests.api;

import by.moby.family.requests.RequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/family")
class RequestController {

    record TopUpRequestBody(@NotBlank String childId, String relativeId, String amount, String reason) {
    }

    record P2pOverRequestBody(@NotBlank String childId, String contactId, String amount) {
    }

    record CreatedView(RequestView request, String relativeId, String relativeLabel, String relativeDative) {
    }

    private final RequestService requests;
    private final RequestMapper mapper;

    RequestController(RequestService requests, RequestMapper mapper) {
        this.requests = requests;
        this.mapper = mapper;
    }

    @GetMapping("/requests")
    List<RequestView> list() {
        return mapper.toViews(requests.visible());
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    CreatedView createTopUp(@Valid @RequestBody TopUpRequestBody body) {
        return created(requests.createTopUp(body.childId(), body.relativeId(), body.amount(), body.reason()));
    }

    @PostMapping("/requests/p2p-over")
    @ResponseStatus(HttpStatus.CREATED)
    CreatedView createP2pOver(@Valid @RequestBody P2pOverRequestBody body) {
        return created(requests.createP2pOver(body.childId(), body.contactId(), body.amount()));
    }

    @PostMapping("/requests/{id}/approve")
    RequestView approve(@PathVariable String id) {
        return mapper.toView(requests.approve(id));
    }

    @PostMapping("/requests/{id}/decline")
    RequestView decline(@PathVariable String id) {
        return mapper.toView(requests.decline(id));
    }

    private CreatedView created(RequestService.Created c) {
        return new CreatedView(mapper.toView(c.request()), c.relative().getId(), c.relative().getLabel(), c.relative().getDative());
    }
}
