package io.dsub.discogs.batch.domain.artist;

import lombok.Data;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@Data
@XmlRootElement(name = "artist")
@XmlAccessorType(XmlAccessType.FIELD)
public class ArtistCommand {
    @XmlElement(name = "id")
    private Long id;

    @XmlElement(name = "name")
    private String name;

    @XmlElement(name = "realname")
    private String realName;

    @XmlElement(name = "profile")
    private String profile;

    @XmlElement(name = "data_quality")
    private String dataQuality;
}
