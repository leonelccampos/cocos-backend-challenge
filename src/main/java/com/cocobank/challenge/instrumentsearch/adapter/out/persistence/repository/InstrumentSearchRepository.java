package com.cocobank.challenge.instrumentsearch.adapter.out.persistence.repository;

import com.cocobank.challenge.instrumentsearch.adapter.out.persistence.entity.InstrumentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface InstrumentSearchRepository extends Repository<InstrumentEntity, Integer> {

  @Query(
      value =
          """
          SELECT i.id AS "id",
                 i.ticker AS "ticker",
                 i.name AS "name",
                 i.type AS "type",
                 valid.close AS "latestPositiveClose",
                 valid.date AS "latestPositiveCloseDate",
                 recent.previousclose AS "latestDatedPreviousClose"
          FROM instruments i
          LEFT JOIN LATERAL (
            SELECT md.close, md.date
            FROM marketdata md
            WHERE md.instrumentid = i.id
              AND md.date IS NOT NULL
              AND md.close > 0
            ORDER BY md.date DESC, md.id DESC
            LIMIT 1
          ) valid ON TRUE
          LEFT JOIN LATERAL (
            SELECT md.previousclose
            FROM marketdata md
            WHERE md.instrumentid = i.id
              AND md.date IS NOT NULL
            ORDER BY md.date DESC, md.id DESC
            LIMIT 1
          ) recent ON TRUE
          """,
      nativeQuery = true)
  List<InstrumentSearchProjection> findAllInstrumentSearchCandidates();
}
