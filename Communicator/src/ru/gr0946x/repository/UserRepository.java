package ru.gr0946x.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.gr0946x.entity.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @Query("SELECT u FROM User u WHERE LOWER(u.nick) = LOWER(:nick)")
    Optional<User> findByNickIgnoreCase(@Param("nick") String nick);

    boolean existsByNickIgnoreCase(String nick);
}
