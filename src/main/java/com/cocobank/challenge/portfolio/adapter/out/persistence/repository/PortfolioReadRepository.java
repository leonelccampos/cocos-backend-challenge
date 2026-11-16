package com.cocobank.challenge.portfolio.adapter.out.persistence.repository;

import com.cocobank.challenge.portfolio.adapter.out.persistence.entity.UserEntity;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface PortfolioReadRepository extends Repository<UserEntity, Integer> {

  @Query(
      value =
          """
          SELECT EXISTS (
            SELECT 1
            FROM users u
            WHERE u.id = :userId
              AND u.accountnumber = :accountNumber
          )
          """,
      nativeQuery = true)
  boolean existsByUserIdAndAccountNumber(
      @Param("userId") int userId, @Param("accountNumber") String accountNumber);

  @Query(
      value =
          """
          SELECT o.instrumentid AS "instrumentId",
                 o.size AS "size",
                 o.price AS "price",
                 o.side AS "side",
                 o.status AS "status",
                 o.datetime AS "dateTime",
                 i.ticker AS "ticker",
                 i.name AS "name",
                 i.type AS "instrumentType"
          FROM orders o
          LEFT JOIN instruments i ON i.id = o.instrumentid
          WHERE o.userid = :userId
            AND (
              (o.status = 'FILLED' AND o.side IN ('CASH_IN', 'CASH_OUT', 'BUY', 'SELL'))
              OR (o.status = 'NEW' AND o.side = 'BUY')
            )
          ORDER BY o.id
          """,
      nativeQuery = true)
  List<OrderMovementProjection> findOrderMovementsByUserId(@Param("userId") int userId);

  @Query(
      value =
          """
          WITH ranked_prices AS (
            SELECT md.instrumentid,
                   md.id,
                   md.close,
                   md.previousclose,
                   md.date,
                   ROW_NUMBER() OVER (
                     PARTITION BY md.instrumentid
                     ORDER BY md.date DESC, md.id DESC
                   ) AS row_number
            FROM marketdata md
            WHERE md.instrumentid IN (:instrumentIds)
              AND md.date IS NOT NULL
          )
          SELECT recent.instrumentid AS "instrumentId",
                 COALESCE(valid.close,
                   CASE WHEN recent.previousclose > 0
                     THEN recent.previousclose ELSE recent.close END) AS "close",
                 COALESCE(valid.date, recent.date) AS "date",
                 CASE WHEN valid.id = recent.id THEN valid.previousclose
                   ELSE NULL END AS "previousClose",
                 CASE
                   WHEN valid.id IS NOT NULL AND valid.id <> recent.id
                     THEN 'HISTORICAL_CLOSE'
                   WHEN valid.id IS NULL AND recent.previousclose > 0
                     THEN 'PREVIOUS_CLOSE'
                   ELSE NULL
                 END AS "fallbackSource"
          FROM ranked_prices recent
          LEFT JOIN LATERAL (
            SELECT md.id, md.close, md.previousclose, md.date
            FROM marketdata md
            WHERE md.instrumentid = recent.instrumentid
              AND md.date IS NOT NULL
              AND md.close > 0
            ORDER BY md.date DESC, md.id DESC
            LIMIT 1
          ) valid ON TRUE
          WHERE recent.row_number = 1
          """,
      nativeQuery = true)
  List<MarketPriceProjection> findLatestPricesByInstrumentIds(
      @Param("instrumentIds") List<Integer> instrumentIds);
}
