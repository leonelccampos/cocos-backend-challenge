package com.cocobank.challenge.portfolio.application.port.out;

import com.cocobank.challenge.portfolio.application.dto.PortfolioReadData;
import java.util.Optional;

public interface PortfolioReadPort {

  Optional<PortfolioReadData> findByUserIdAndAccountNumber(int userId, String accountNumber);
}
