package com.data.oai.pubmed.oa;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class OaRecord {

    @XmlAttribute
    private String id;

    @XmlAttribute
    private String citation;

    @XmlAttribute
    private String license;

    @XmlAttribute
    private String retracted;

    @XmlElement(name = "link")
    private List<OaLink> links;

    public String getId() { return id; }
    public String getLicense() { return license; }
    public boolean isRetracted() { return "yes".equalsIgnoreCase(retracted); }
    public List<OaLink> getLinks() { return links; }
}
