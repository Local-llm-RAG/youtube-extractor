package com.data.youtube.event;

public record VideoDiscoveredEvent(String youtubeVideoId, Long channelDbId, java.util.List<String> desiredLanguages,
                                   java.util.Map<String, java.util.Map.Entry<String, String>> categoryMap) {}
