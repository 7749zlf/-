package com.shortvideo.backend.h5;

import java.util.List;

import com.shortvideo.backend.h5.dto.ApiOkResponse;
import com.shortvideo.backend.h5.dto.EpisodeInteractionRequest;
import com.shortvideo.backend.h5.dto.FollowDramaRequest;
import com.shortvideo.backend.h5.dto.FollowedDramaResponse;
import com.shortvideo.backend.h5.dto.InteractionStateResponse;
import com.shortvideo.backend.h5.dto.LikedEpisodeResponse;
import com.shortvideo.backend.h5.dto.PlayEventRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/h5")
public class H5InteractionController {

    private final H5UserService userService;

    public H5InteractionController(H5UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/play-events")
    public ApiOkResponse playEvent(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) PlayEventRequest request
    ) {
        return userService.recordPlayEvent(request, authorization);
    }

    @GetMapping("/me/follows")
    public List<FollowedDramaResponse> follows(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String deviceId
    ) {
        return userService.listFollowedDramas(authorization, deviceId);
    }

    @GetMapping("/me/interactions")
    public InteractionStateResponse interactions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) Long dramaId,
            @RequestParam(required = false) String episodeId
    ) {
        return userService.interactionState(authorization, deviceId, dramaId, episodeId);
    }

    @PostMapping("/me/follows")
    public InteractionStateResponse follow(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) Long dramaId,
            @RequestBody(required = false) FollowDramaRequest request
    ) {
        return userService.followDrama(
                authorization,
                resolveDeviceId(request == null ? null : request.deviceId(), deviceId),
                resolveDramaId(request == null ? null : request.dramaId(), dramaId)
        );
    }

    @DeleteMapping("/me/follows")
    public InteractionStateResponse unfollow(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) Long dramaId,
            @RequestBody(required = false) FollowDramaRequest request
    ) {
        return userService.unfollowDrama(
                authorization,
                resolveDeviceId(request == null ? null : request.deviceId(), deviceId),
                resolveDramaId(request == null ? null : request.dramaId(), dramaId)
        );
    }

    @DeleteMapping("/me/follows/{dramaId}")
    public InteractionStateResponse unfollow(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String deviceId,
            @PathVariable long dramaId,
            @RequestBody(required = false) FollowDramaRequest request
    ) {
        return userService.unfollowDrama(
                authorization,
                resolveDeviceId(request == null ? null : request.deviceId(), deviceId),
                dramaId
        );
    }

    @GetMapping("/me/likes")
    public List<LikedEpisodeResponse> likes(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String deviceId
    ) {
        return userService.listLikedEpisodes(authorization, deviceId);
    }

    @PostMapping("/episodes/{episodeId}/like")
    public InteractionStateResponse likeEpisodeByPath(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String deviceId,
            @PathVariable String episodeId,
            @RequestBody(required = false) EpisodeInteractionRequest request
    ) {
        return userService.likeEpisode(
                authorization,
                resolveDeviceId(request == null ? null : request.deviceId(), deviceId),
                resolveEpisodeId(request == null ? null : request.episodeId(), episodeId)
        );
    }

    @PostMapping("/me/likes")
    public InteractionStateResponse like(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String episodeId,
            @RequestBody(required = false) EpisodeInteractionRequest request
    ) {
        return userService.likeEpisode(
                authorization,
                resolveDeviceId(request == null ? null : request.deviceId(), deviceId),
                resolveEpisodeId(request == null ? null : request.episodeId(), episodeId)
        );
    }

    @DeleteMapping("/episodes/{episodeId}/like")
    public InteractionStateResponse unlikeEpisodeByPath(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String deviceId,
            @PathVariable String episodeId,
            @RequestBody(required = false) EpisodeInteractionRequest request
    ) {
        return userService.unlikeEpisode(
                authorization,
                resolveDeviceId(request == null ? null : request.deviceId(), deviceId),
                resolveEpisodeId(request == null ? null : request.episodeId(), episodeId)
        );
    }

    @DeleteMapping("/me/likes")
    public InteractionStateResponse unlike(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String episodeId,
            @RequestBody(required = false) EpisodeInteractionRequest request
    ) {
        return userService.unlikeEpisode(
                authorization,
                resolveDeviceId(request == null ? null : request.deviceId(), deviceId),
                resolveEpisodeId(request == null ? null : request.episodeId(), episodeId)
        );
    }

    private String resolveDeviceId(String bodyDeviceId, String queryDeviceId) {
        return bodyDeviceId == null || bodyDeviceId.isBlank() ? queryDeviceId : bodyDeviceId;
    }

    private long resolveDramaId(Long bodyDramaId, Long queryDramaId) {
        Long dramaId = bodyDramaId != null ? bodyDramaId : queryDramaId;
        if (dramaId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dramaId is required");
        }
        return dramaId;
    }

    private String resolveEpisodeId(String bodyEpisodeId, String routeEpisodeId) {
        String episodeId = bodyEpisodeId == null || bodyEpisodeId.isBlank() ? routeEpisodeId : bodyEpisodeId;
        if (episodeId == null || episodeId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "episodeId is required");
        }
        return episodeId;
    }
}
