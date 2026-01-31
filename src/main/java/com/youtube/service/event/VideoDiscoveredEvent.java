package com.youtube.service.event;

public record VideoDiscoveredEvent(String youtubeVideoId, Long channelDbId, java.util.List<String> desiredLanguages,
                                   java.util.Map<String,java.util.Map<String, String>> categoryMap) {}
