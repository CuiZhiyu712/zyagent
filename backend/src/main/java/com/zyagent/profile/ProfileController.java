package com.zyagent.profile;

import com.zyagent.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /** 当前 owner 的画像视图：已确认技能 + 每项技能的证据列表。 */
    @GetMapping
    public ApiResponse<ProfileView> profile() {
        String owner = profileService.currentOwnerId();
        List<UserSkill> skills = profileService.skills(owner);
        Map<String, List<SkillEvidence>> evidence = new LinkedHashMap<>();
        for (UserSkill skill : skills) {
            evidence.put(skill.skillKey(), profileService.evidence(owner, skill.skillKey()));
        }
        return ApiResponse.ok(new ProfileView(owner, skills, evidence));
    }

    @GetMapping("/skills")
    public ApiResponse<List<UserSkill>> skills() {
        return ApiResponse.ok(profileService.skills(profileService.currentOwnerId()));
    }

    /** 手动新增/更正技能；可带 expectedVersion 做乐观并发校验。 */
    @PutMapping("/skills/{skillKey}")
    public ApiResponse<UserSkill> upsertSkill(@PathVariable String skillKey,
                                              @RequestBody UpsertSkillRequest request) {
        return ApiResponse.ok(profileService.upsertSkill(profileService.currentOwnerId(), skillKey,
            request.level(), request.confidence(), request.evidence(), request.expectedVersion()));
    }

    @DeleteMapping("/skills/{skillKey}")
    public ApiResponse<Void> deleteSkill(@PathVariable String skillKey) {
        profileService.deleteSkill(profileService.currentOwnerId(), skillKey);
        return ApiResponse.ok(null);
    }

    @GetMapping("/skills/{skillKey}/evidence")
    public ApiResponse<List<SkillEvidence>> evidence(@PathVariable String skillKey) {
        return ApiResponse.ok(profileService.evidence(profileService.currentOwnerId(), skillKey));
    }

    @GetMapping("/skills/{skillKey}/history")
    public ApiResponse<List<SkillHistoryEntry>> history(@PathVariable String skillKey) {
        return ApiResponse.ok(profileService.history(profileService.currentOwnerId(), skillKey));
    }

    @GetMapping("/suggestions")
    public ApiResponse<List<ProfileSuggestion>> suggestions(
        @RequestParam(required = false) ProfileSuggestionState state
    ) {
        return ApiResponse.ok(profileService.suggestions(profileService.currentOwnerId(), state));
    }

    @PostMapping("/suggestions")
    public ApiResponse<ProfileSuggestion> suggest(@RequestBody SuggestionRequest request) {
        return ApiResponse.ok(profileService.suggest(profileService.currentOwnerId(), request.skillKey(),
            request.level(), request.evidence(), request.sourceType(), request.sourceId()));
    }

    @PutMapping("/suggestions/{id}")
    public ApiResponse<ProfileSuggestion> editSuggestion(@PathVariable String id,
                                                         @RequestBody EditSuggestionRequest request) {
        return ApiResponse.ok(profileService.editSuggestion(id, request.level(), request.evidence()));
    }

    @PostMapping("/suggestions/{id}/approve")
    public ApiResponse<UserSkill> approve(@PathVariable String id) {
        return ApiResponse.ok(profileService.approve(id));
    }

    @PostMapping("/suggestions/{id}/reject")
    public ApiResponse<ProfileSuggestion> reject(@PathVariable String id,
                                                 @RequestBody(required = false) RejectRequest request) {
        return ApiResponse.ok(profileService.reject(id, request == null ? "" : request.note()));
    }

    @ExceptionHandler(ProfileVersionConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleConflict(ProfileVersionConflictException ex) {
        return ApiResponse.fail(ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleIllegalState(IllegalStateException ex) {
        return ApiResponse.fail(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException ex) {
        return ApiResponse.fail(ex.getMessage());
    }

    public record ProfileView(String ownerId, List<UserSkill> skills, Map<String, List<SkillEvidence>> evidence) {
    }

    public record SuggestionRequest(String skillKey, SkillLevel level, String evidence,
                                    String sourceType, String sourceId) {
    }

    public record EditSuggestionRequest(SkillLevel level, String evidence) {
    }

    public record RejectRequest(String note) {
    }

    public record UpsertSkillRequest(SkillLevel level, Double confidence, String evidence, Integer expectedVersion) {
    }
}
