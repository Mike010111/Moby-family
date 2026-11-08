package by.moby.payment.controls;

import by.moby.payment.account.Account;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/payment/children/{childId}")
class ControlsController {

    record FrozenRequest(@NotNull Boolean frozen) {
    }

    record FrozenView(String childId, boolean frozen) {
    }

    record CategoryRequest(@NotNull Boolean allowed) {
    }

    record PermissionsView(String childId, Map<String, Boolean> allow) {
    }

    private final ControlsService controls;

    ControlsController(ControlsService controls) {
        this.controls = controls;
    }

    @PutMapping("/frozen")
    FrozenView setFrozen(@PathVariable String childId, @Valid @RequestBody FrozenRequest request) {
        Account child = controls.setFrozen(childId, request.frozen());
        return new FrozenView(childId, child.isFrozen());
    }

    @PutMapping("/categories/{key}")
    PermissionsView setCategory(@PathVariable String childId, @PathVariable String key, @Valid @RequestBody CategoryRequest request) {
        Account child = controls.setCategory(childId, key, request.allowed());
        return new PermissionsView(childId, child.getPermissions());
    }
}
