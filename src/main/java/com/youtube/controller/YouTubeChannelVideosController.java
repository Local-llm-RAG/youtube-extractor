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
            @RequestParam List<String> desiredLanguages,
            @RequestParam Boolean retryOnlyThoseWithoutTranscripts
    ) throws Exception {
        return ResponseEntity.ok(service.fetchAndSaveAllVideoIdsByHandle(handle, runTranscriptsSavingForAll, desiredLanguages, retryOnlyThoseWithoutTranscripts));
    }

    @Operation(summary = "Get video by url")
    @GetMapping("/video")
    public ResponseEntity<Void> getVideoByUrl(
            @RequestParam String videoUrl,
            @RequestParam List<String> desiredLanguages
    ) throws Exception {
        service.fetchAndSaveVideoByUrl(videoUrl, desiredLanguages);
        return ResponseEntity.accepted().build();
    }
}
