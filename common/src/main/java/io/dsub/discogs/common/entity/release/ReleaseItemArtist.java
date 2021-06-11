package io.dsub.discogs.common.entity.release;

import io.dsub.discogs.common.entity.artist.Artist;
import io.dsub.discogs.common.entity.base.BaseTimeEntity;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Entity
@Builder
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Table(
    name = "release_item_artist",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_release_item_artist_release_item_id_artist_id",
            columnNames = {"release_item_id", "artist_id"}))
public class ReleaseItemArtist extends BaseTimeEntity {

  private static final Long SerialVersionUID = 1L;

  @Id
  @Column(name = "id", columnDefinition = "serial")
  @GeneratedValue(strategy = GenerationType.SEQUENCE)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "release_item_id", referencedColumnName = "id")
  private ReleaseItem releaseItem;

  @ManyToOne
  @JoinColumn(name = "artist_id", referencedColumnName = "id")
  private Artist artist;
}