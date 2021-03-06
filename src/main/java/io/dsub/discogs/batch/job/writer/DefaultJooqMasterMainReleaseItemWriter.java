package io.dsub.discogs.batch.job.writer;

import io.dsub.discogs.jooq.tables.Master;
import io.dsub.discogs.jooq.tables.records.MasterRecord;
import java.util.LinkedList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Query;

@RequiredArgsConstructor
public class DefaultJooqMasterMainReleaseItemWriter implements JooqItemWriter<MasterRecord> {

  private final DSLContext context;

  @Override
  public void write(List<? extends MasterRecord> items) {

    if (items.isEmpty()) {
      return;
    }

    List<Query> updates = new LinkedList<>();
    items.forEach(record -> updates.add(getQuery(record)));
    context.batch(updates).execute();
  }

  @Override
  public Query getQuery(MasterRecord record) {
    return context
        .update(Master.MASTER)
        .set(Master.MASTER.LAST_MODIFIED_AT, record.getLastModifiedAt())
        .set(Master.MASTER.MAIN_RELEASE_ID, record.getMainReleaseId())
        .where(Master.MASTER.ID.eq(record.getId()));
  }
}
