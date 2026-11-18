package com.cocobank.challenge.application.port.out;

import com.cocobank.challenge.domain.user.User;
import java.util.Optional;

public interface UserPort {

  boolean lock(int userId);

  Optional<User> findById(int userId);
}
