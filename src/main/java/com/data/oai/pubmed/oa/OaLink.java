package com.data.oai.pubmed.oa;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.FIELD)
public class OaLink {

    @XmlAttribute
    private String format;

    @XmlAttribute
    private String href;

    public String getFormat() { return format; }
    public String getHref() { return href; }
}
