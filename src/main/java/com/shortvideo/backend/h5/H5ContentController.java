package com.shortvideo.backend.h5;

import java.util.List;

import com.shortvideo.backend.h5.dto.DramaResponse;
import com.shortvideo.backend.h5.dto.EpisodeAccessRequest;
import com.shortvideo.backend.h5.dto.EpisodeAccessResponse;
import com.shortvideo.backend.h5.dto.EpisodeResponse;
import com.shortvideo.backend.h5.dto.StorylineResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/h5")
public class H5ContentController {

    private final H5Service h5Service;

    public H5ContentController(H5Service h5Service) {
        this.h5Service = h5Service;
    }

    @GetMapping("/dramas")
    public List<DramaResponse> listDramas() {
        return h5Service.listDramas();
    }

    @GetMapping("/dramas/default")
    public DramaResponse getDefaultDrama() {
        return h5Service.getDefaultDrama();
    }

    @GetMapping("/dramas/{dramaId}")
    public DramaResponse getDrama(@PathVariable long dramaId) {
        return h5Service.getDrama(dramaId);
    }

    @GetMapping("/episodes")
    public List<EpisodeResponse> listEpisodes(
            @RequestParam(required = false) Long dramaId,
            @RequestParam(required = false) String deviceId
    ) {
        return h5Service.listEpisodes(dramaId, deviceId);
    }

    @GetMapping("/dramas/{dramaId}/episodes")
    public List<EpisodeResponse> listDramaEpisodes(
            @PathVariable long dramaId,
            @RequestParam(required = false) String deviceId
    ) {
        return h5Service.listEpisodes(dramaId, deviceId);
    }

    @PostMapping("/episodes/access-check")
    public EpisodeAccessResponse checkEpisodeAccess(@RequestBody(required = false) EpisodeAccessRequest request) {
        return h5Service.checkEpisodeAccess(request);
    }

    @GetMapping("/storylines")
    public List<StorylineResponse> listStorylines(@RequestParam(required = false) Long dramaId) {
        return h5Service.listStorylines(dramaId);
    }
}
