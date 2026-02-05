package com.youtube.arxiv;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.OK;

@RestController
@Slf4j
@RequestMapping("/api/arxiv")
@RequiredArgsConstructor
public class ArxivController {
    private final ArxivService arxivService;

    @GetMapping("/search")
    @ResponseStatus(OK)
    public List<ArxivPaper> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "10") int limit
    ) {
        log.info("GET /api/arxiv/search?query={}&start={}&limit={}", query, start, limit);

        return arxivService.search(query, start, limit);
    }
}
