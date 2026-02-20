package com.data.arxiv;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static java.util.Objects.isNull;
import static org.springframework.http.HttpStatus.OK;

@RestController
@Slf4j
@RequestMapping("/api/arxiv")
@RequiredArgsConstructor
public class ArxivController {
    private final ArxivService arxivService;

    @PostMapping("/search")
    @ResponseStatus(OK)
    public List<Paper> search(@RequestBody ArxivSearchRequest request) {
        log.info("GET /api/arxiv/search - start={} limit={}", request.getStart(), request.getLimit());

        Integer limit = request.getLimit();
        Integer start = request.getStart();

        if (isNull(limit) || limit < 1) {
            throw new IllegalArgumentException("limit must not be less than 1");
        }

        if (isNull(start) || start < 0) {
            throw new IllegalArgumentException("start must not be less than 0");
        }

        return arxivService.search(request);
    }
}
