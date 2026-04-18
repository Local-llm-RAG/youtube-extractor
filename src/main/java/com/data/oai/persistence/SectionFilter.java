package com.data.oai.persistence;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Set;

@Getter
@Setter
@Accessors(chain = true)
public class SectionFilter {
    private Set<String> includeTitles;
    private Set<String> excludeTitles;
    private Integer maxSections;
    private Integer maxCharsTotal;
}
