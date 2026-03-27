package com.data.oai.arxiv.search;

import jakarta.validation.Valid;
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
    public List<Paper> search(@Valid @RequestBody ArxivSearchRequest request) {
        log.info("GET /api/arxiv/search - start={} limit={}", request.getStart(), request.getLimit());

        return arxivService.search(request);
    }
}
