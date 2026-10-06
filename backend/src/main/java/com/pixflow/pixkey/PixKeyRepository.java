package com.pixflow.pixkey;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pixflow.account.Account;

import java.util.UUID;
import java.util.Optional;

public interface PixKeyRepository extends JpaRepository<PixKey, UUID> {

  Optional<PixKey> findByKeyValue(String keyValue);

  @Query("select p.account from PixKey p where p.keyValue = :pixKeyValue")
  Optional<Account> findAccountByPixKey(@Param("pixKeyValue") String pixKeyValue);
}
  