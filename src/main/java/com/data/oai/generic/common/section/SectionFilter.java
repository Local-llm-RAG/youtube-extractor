package com.data.oai.generic.common.section;

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
    private Integer maxLevel;
    private Integer maxCharsTotal;
}
