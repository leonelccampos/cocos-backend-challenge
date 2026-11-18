package com.cocobank.challenge.adapter.out.persistence.repository;

import com.cocobank.challenge.adapter.out.persistence.entity.UserEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, Integer> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT user FROM UserEntity user WHERE user.id = :userId")
  Optional<UserEntity> lockById(@Param("userId") int userId);
}
