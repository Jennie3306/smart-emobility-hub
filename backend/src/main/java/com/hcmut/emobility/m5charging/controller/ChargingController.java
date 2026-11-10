package com.hcmut.emobility.m5charging.controller;

import com.hcmut.emobility.m5charging.domain.ChargingPriorityAudit;
import com.hcmut.emobility.m5charging.dto.ChargingHubSummary;
import com.hcmut.emobility.m5charging.dto.ChargingRequest;
import com.hcmut.emobility.m5charging.dto.ChargingScheduleRequest;
import com.hcmut.emobility.m5charging.dto.ChargingSessionResponse;
import com.hcmut.emobility.m5charging.dto.PriorityChangeRequest;
import com.hcmut.emobility.m5charging.dto.ScheduleMoveRequest;
import com.hcmut.emobility.m5charging.service.ChargingService;
import com.hcmut.emobility.shared.auth.AppUser;
import com.hcmut.emobility.shared.auth.AppUserRepository;
import com.hcmut.emobility.shared.common.BusinessException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/api/m5")
public class ChargingController {

    private final ChargingService charging;
    private final AppUserRepository users;

    public ChargingController(ChargingService charging, AppUserRepository users) {
        this.charging = charging;
        this.users = users;
    }

    @GetMapping("/hubs")
    public List<ChargingHubSummary> hubs() {
        return charging.hubs();
    }

    @GetMapping("/sessions/mine")
    public List<ChargingSessionResponse> mine(Authentication auth) {
        return charging.mine(currentUserId(auth));
    }

    @GetMapping("/hubs/{hubId}/queue")
    public List<ChargingSessionResponse> hubQueue(@PathVariable Long hubId, Authentication auth) {
        requireOperator(auth);
        return charging.hubSessions(hubId);
    }

    @PostMapping("/sessions")
    public ResponseEntity<ChargingSessionResponse> request(@Valid @RequestBody ChargingRequest body,
                                                            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(charging.request(currentUserId(auth), body));
    }

    @PostMapping("/sessions/schedule")
    public ResponseEntity<ChargingSessionResponse> schedule(@Valid @RequestBody ChargingScheduleRequest body,
                                                             Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(charging.schedule(currentUserId(auth), body));
    }

    @PostMapping("/sessions/{id}/start")
    public ChargingSessionResponse start(@PathVariable Long id, Authentication auth) {
        return charging.start(id, currentUserId(auth), isOperator(auth));
    }

    @PostMapping("/sessions/{id}/cancel")
    public ChargingSessionResponse cancel(@PathVariable Long id, Authentication auth) {
        return charging.cancel(id, currentUserId(auth), isOperator(auth));
    }

    @PutMapping("/sessions/{id}/schedule")
    public ChargingSessionResponse moveSchedule(@PathVariable Long id,
                                                  @Valid @RequestBody ScheduleMoveRequest body,
                                                  Authentication auth) {
        requireOperator(auth);
        return charging.moveSchedule(id, body.startAt(), body.durationMinutes(), currentUserId(auth), body.reason());
    }

    @PatchMapping("/sessions/{id}/priority")
    public ChargingSessionResponse priority(@PathVariable Long id,
                                             @Valid @RequestBody PriorityChangeRequest body,
                                             Authentication auth) {
        requireOperator(auth);
        return charging.changePriority(id, currentUserId(auth), body.priority(), body.reason());
    }

    @GetMapping("/sessions/{id}/audit")
    public ResponseEntity<List<ChargingPriorityAudit>> audit(@PathVariable Long id, Authentication auth) {
        requireOperator(auth);
        return ResponseEntity.ok(charging.auditForSession(id));
    }

    private Long currentUserId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new BusinessException("UNAUTHENTICATED", "Sign in to continue", HttpStatus.UNAUTHORIZED);
        }
        return users.findByUsername(auth.getName()).map(AppUser::getId).orElseThrow(() ->
                new BusinessException("UNAUTHENTICATED", "Invalid login session", HttpStatus.UNAUTHORIZED));
    }

    private boolean isOperator(Authentication auth) {
        return auth != null && auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_OPERATOR"));
    }

    private void requireOperator(Authentication auth) {
        if (!isOperator(auth)) {
            throw new BusinessException("ACCESS_DENIED", "This operation requires an Operator account", HttpStatus.FORBIDDEN);
        }
    }
}
