package com.youtube.controller;

import com.youtube.service.youtube.YouTubeChannelVideosService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/channels")
@RequiredArgsConstructor
public class YouTubeChannelVideosController {

    private final YouTubeChannelVideosService service;


    @Operation(summary = "Get all video IDs for a channel handle")
    @GetMapping("/videos")
    public ResponseEntity<List<String>> getVideoIds(
            @RequestParam String handle,
            @RequestParam Boolean runTranscriptsSavingForAll,
            @RequestParam String regionCode
            ) throws Exception {
        return ResponseEntity.ok(service.fetchAndSaveAllVideoIdsByHandle(handle, runTranscriptsSavingForAll, regionCode));
    }
}
