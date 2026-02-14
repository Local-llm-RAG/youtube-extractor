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

    @PostMapping("/search")
    @ResponseStatus(OK)
    public List<ArxivPaper> search(@RequestBody ArxivSearchRequest request) {
        log.info("GET /api/arxiv/search - start={} limit={}", request.getStart(), request.getLimit());

        if (0 == request.getLimit()) {
            throw new IllegalArgumentException("limit must not be less than 1");
        }

        return arxivService.search(request);
    }
}
