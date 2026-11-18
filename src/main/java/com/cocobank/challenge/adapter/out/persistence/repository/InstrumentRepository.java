package com.cocobank.challenge.adapter.out.persistence.repository;

import com.cocobank.challenge.adapter.out.persistence.entity.InstrumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstrumentRepository extends JpaRepository<InstrumentEntity, Integer> {}
