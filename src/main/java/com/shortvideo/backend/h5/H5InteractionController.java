package com.shortvideo.backend.h5;

import com.shortvideo.backend.h5.dto.ApiOkResponse;
import com.shortvideo.backend.h5.dto.PlayEventRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/h5")
public class H5InteractionController {

    private final H5UserService userService;

    public H5InteractionController(H5UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/play-events")
    public ApiOkResponse playEvent(@RequestBody(required = false) PlayEventRequest request) {
        return userService.recordPlayEvent(request);
    }

    @PostMapping("/me/follows")
    public ApiOkResponse follow(
            @RequestParam(required = false) String deviceId,
            @RequestParam long dramaId
    ) {
        return userService.followDrama(deviceId, dramaId);
    }

    @DeleteMapping("/me/follows/{dramaId}")
    public ApiOkResponse unfollow(
            @RequestParam(required = false) String deviceId,
            @PathVariable long dramaId
    ) {
        return userService.unfollowDrama(deviceId, dramaId);
    }

    @PostMapping("/episodes/{episodeId}/like")
    public ApiOkResponse like(
            @RequestParam(required = false) String deviceId,
            @PathVariable String episodeId
    ) {
        return userService.likeEpisode(deviceId, episodeId);
    }

    @DeleteMapping("/episodes/{episodeId}/like")
    public ApiOkResponse unlike(
            @RequestParam(required = false) String deviceId,
            @PathVariable String episodeId
    ) {
        return userService.unlikeEpisode(deviceId, episodeId);
    }
}
