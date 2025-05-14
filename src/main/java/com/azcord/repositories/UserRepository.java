package com.azcord.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.azcord.models.User;


@Repository
public interface UserRepository extends JpaRepository<User, Long> {
	Optional<User> findByUsername(String username); 
    Optional<User> findByEmail(String email);
    
    List<User> findTop10ByUsernameContainingIgnoreCase(String username);
    
    @Query("SELECT u.id FROM User u WHERE u.username = :username")
    Long findIdByUsername(String username);
}
