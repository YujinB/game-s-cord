package com.example.gamescord.global.init;

import com.example.gamescord.wallet.domain.model.Account;
import com.example.gamescord.wallet.domain.model.AccountType;
import com.example.gamescord.wallet.domain.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SystemAccountInitializer implements ApplicationRunner {

    private final AccountRepository accountRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        for(AccountType type : AccountType.values()){
            // 시스템 계좌만 미리 개설
            if(!type.isUserOwned()){
                accountRepository.insertIfAbsent(Account.createSystemAccount(type));
            }
        }
    }
}
