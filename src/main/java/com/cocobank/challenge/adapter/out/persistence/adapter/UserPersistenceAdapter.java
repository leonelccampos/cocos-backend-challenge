package com.cocobank.challenge.adapter.out.persistence.adapter;

import com.cocobank.challenge.adapter.out.persistence.entity.UserEntity;
import com.cocobank.challenge.adapter.out.persistence.repository.UserRepository;
import com.cocobank.challenge.application.port.out.UserPort;
import com.cocobank.challenge.domain.user.User;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class UserPersistenceAdapter implements UserPort {

  private final UserRepository repository;

  public UserPersistenceAdapter(UserRepository repository) {
    this.repository = repository;
  }

  @Override
  public boolean lock(int userId) {
    return repository.lockById(userId).isPresent();
  }

  @Override
  public Optional<User> findById(int userId) {
    return repository.findById(userId).map(UserEntity::toDomain);
  }
}
