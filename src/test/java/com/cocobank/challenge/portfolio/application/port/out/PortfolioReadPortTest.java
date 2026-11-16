package com.cocobank.challenge.portfolio.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocobank.challenge.portfolio.application.dto.PortfolioReadData;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PortfolioReadPortTest {

  @Test
  void distinguishesMissingAccountFromExistingAccountWithoutMovements() {
    PortfolioReadPort missingAccount = (userId, accountNumber) -> Optional.empty();
    PortfolioReadPort accountWithoutMovements =
        (userId, accountNumber) -> Optional.of(new PortfolioReadData(List.of(), Map.of()));

    assertThat(missingAccount.findByUserIdAndAccountNumber(1, "123456")).isEmpty();
    assertThat(accountWithoutMovements.findByUserIdAndAccountNumber(1, "123456"))
        .contains(new PortfolioReadData(List.of(), Map.of()));
  }
}
