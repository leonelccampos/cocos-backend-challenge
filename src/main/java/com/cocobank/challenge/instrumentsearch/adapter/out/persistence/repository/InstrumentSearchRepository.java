package com.cocobank.challenge.instrumentsearch.adapter.out.persistence.repository;

import com.cocobank.challenge.instrumentsearch.adapter.out.persistence.entity.InstrumentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface InstrumentSearchRepository extends Repository<InstrumentEntity, Integer> {

  @Query(
      value =
          """
          WITH search_query AS (
            SELECT lower(
              regexp_replace(normalize(CAST(:query AS text), NFD),
                             U&'[\\0300-\\036f]', '', 'g') COLLATE "und-x-icu"
            ) AS value
          )
          SELECT i.id AS "id",
                 i.ticker AS "ticker",
                 i.name AS "name",
                 i.type AS "type",
                 COALESCE(valid.close,
                   CASE WHEN recent.previousclose > 0 THEN recent.previousclose END
                 ) AS "marketPrice",
                 valid.date AS "closeDate"
          FROM instruments i
          CROSS JOIN search_query q
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
          WHERE i.type = 'ACCIONES'
            AND strpos(
              lower(
                regexp_replace(
                  normalize(CASE WHEN :field = 'TICKER' THEN i.ticker ELSE i.name END, NFD),
                  U&'[\\0300-\\036f]', '', 'g'
                ) COLLATE "und-x-icu"
              ),
              q.value
            ) > 0
          ORDER BY i.ticker COLLATE "C" ASC NULLS LAST, i.id ASC
          """,
      nativeQuery = true)
  List<InstrumentSearchProjection> search(
      @Param("query") String query, @Param("field") String field);
}
