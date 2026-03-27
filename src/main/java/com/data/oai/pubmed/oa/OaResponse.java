package com.data.oai.pubmed.oa;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * JAXB mapping for the PMC Open Access Web Service XML response.
 *
 * <p>Example response:
 * <pre>{@code
 * <OA>
 *   <responseDate>2017-03-03 12:56:15</responseDate>
 *   <records returned-count="1" total-count="1">
 *     <record id="PMC5334499" citation="..." license="CC BY" retracted="no">
 *       <link format="tgz" href="ftp://ftp.ncbi.nlm.nih.gov/..."/>
 *       <link format="pdf" href="ftp://ftp.ncbi.nlm.nih.gov/..."/>
 *     </record>
 *   </records>
 * </OA>
 * }</pre>
 */
@XmlRootElement(name = "OA")
@XmlAccessorType(XmlAccessType.FIELD)
public class OaResponse {

    @XmlElement(name = "records")
    private OaRecords records;

    public OaRecords getRecords() {
        return records;
    }
}
