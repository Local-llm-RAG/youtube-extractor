package com.youtube.arxiv;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ArxivSearchRequest {
    private List<String> terms;
    private Integer start;
    private Integer limit;
}

