package com.data.oai.pubmed.oa;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class OaRecords {

    @XmlElement(name = "record")
    private List<OaRecord> records;

    public List<OaRecord> getRecords() {
        return records;
    }
}
