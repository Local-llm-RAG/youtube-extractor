package com.youtube.controller;

import com.youtube.service.youtube.YouTubeChannelVideosService;
import com.youtube.service.youtube.YoutubeTranscriptFetchStrategy;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/channels")
@RequiredArgsConstructor
public class YouTubeChannelVideosController {

    private final YouTubeChannelVideosService service;

    @Operation(summary = "Get all videos metadata and transcripts for given channels")
    @GetMapping("/mass")
    public ResponseEntity<List<String>> getAllVideosForChannels(
            @RequestParam List<String> handles,
            @RequestParam YoutubeTranscriptFetchStrategy fetchStrategy,
            @RequestParam List<String> desiredLanguages,
            @RequestParam(required = false) LocalDate optionalStartDate,
            @RequestParam(required = false) LocalDate optionalEndDate
    ) {
        return ResponseEntity.ok(service.fetchAndSaveAllVideoIdsByMultipleHandles(
                handles,
                fetchStrategy,
                desiredLanguages,
                optionalStartDate,
                optionalEndDate));
    }

    @Operation(summary = "Get all videos metadata and transcripts for a channel handle")
    @GetMapping("/videos")
    public ResponseEntity<List<String>> getVideoIds(
            @RequestParam String handle,
            @RequestParam YoutubeTranscriptFetchStrategy fetchStrategy,
            @RequestParam List<String> desiredLanguages,
            @RequestParam(required = false) LocalDate optionalStartDate,
            @RequestParam(required = false) LocalDate optionalEndDate
    ) throws Exception {
        return ResponseEntity.ok(service.fetchAndSaveAllVideoIdsByHandle(
                handle,
                fetchStrategy,
                desiredLanguages,
                optionalStartDate,
                optionalEndDate));
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
