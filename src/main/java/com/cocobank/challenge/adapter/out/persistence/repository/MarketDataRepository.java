package com.cocobank.challenge.adapter.out.persistence.repository;

import com.cocobank.challenge.adapter.out.persistence.entity.MarketDataEntity;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketDataRepository extends JpaRepository<MarketDataEntity, Integer> {

  @Query(
      """
      SELECT marketData
      FROM MarketDataEntity marketData
      WHERE marketData.instrumentId = :instrumentId
        AND marketData.date IS NOT NULL
        AND marketData.close > 0
      ORDER BY marketData.date DESC, marketData.id DESC
      LIMIT 1
      """)
  Optional<MarketDataEntity> findLastMarketData(@Param("instrumentId") int instrumentId);

  List<MarketDataEntity> findByInstrumentIdInAndDateIsNotNull(Set<Integer> instrumentIds);
}
