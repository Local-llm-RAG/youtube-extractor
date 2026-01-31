package com.youtube.service.event;

public record VideoDiscoveredEvent(String youtubeVideoId, Long channelDbId, String regionCode) {}
