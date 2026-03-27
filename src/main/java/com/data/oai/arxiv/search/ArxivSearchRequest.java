package com.data.oai.arxiv.search;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ArxivSearchRequest {
    private List<String> terms;

    @NotNull
    @Min(0)
    private Integer start;

    @NotNull
    @Min(1)
    private Integer limit;
}

