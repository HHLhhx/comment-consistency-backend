package com.nju.comment.backend.repository;

import com.nju.comment.backend.model.UserCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserCredentialRepository extends JpaRepository<UserCredential, Long> {
    Optional<UserCredential> findByEmail(String email);

    @Query("select uc from UserCredential uc join fetch uc.user u where u.username = :username")
    Optional<UserCredential> findWithUserByUsername(@Param("username") String username);

    @Query("select uc from UserCredential uc join fetch uc.user where uc.email = :email")
    Optional<UserCredential> findWithUserByEmail(@Param("email") String email);
}