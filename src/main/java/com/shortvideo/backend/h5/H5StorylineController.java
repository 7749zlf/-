package com.shortvideo.backend.h5;

import java.util.List;

import com.shortvideo.backend.h5.dto.DrawRequest;
import com.shortvideo.backend.h5.dto.DrawResponse;
import com.shortvideo.backend.h5.dto.StorylineResponse;
import com.shortvideo.backend.h5.dto.UnlockOrderResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/h5")
public class H5StorylineController {

    private final H5Service h5Service;

    public H5StorylineController(H5Service h5Service) {
        this.h5Service = h5Service;
    }

    @PostMapping("/storylines/draw")
    public DrawResponse drawStoryline(@RequestBody(required = false) DrawRequest request) {
        return h5Service.drawStoryline(request);
    }

    @GetMapping("/me/orders")
    public List<UnlockOrderResponse> listOrders(@RequestParam(required = false) String deviceId) {
        return h5Service.listOrders(deviceId);
    }

    @GetMapping("/me/unlocks")
    public List<StorylineResponse> listUnlocks(@RequestParam(required = false) String deviceId) {
        return h5Service.listUnlocks(deviceId);
    }
}
