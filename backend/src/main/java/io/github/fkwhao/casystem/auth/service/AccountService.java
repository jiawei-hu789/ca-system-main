package io.github.fkwhao.casystem.auth.service;

import io.github.fkwhao.casystem.auth.entity.Account;
import io.github.fkwhao.casystem.auth.vo.AccountView;


public interface AccountService {
    Account requireAccount(String username);
    AccountView view(String username);
    AccountView register(String username, String password);

}
