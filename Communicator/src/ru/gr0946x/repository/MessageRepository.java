package ru.gr0946x.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.gr0946x.entity.Message;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    @Query("SELECT m FROM Message m WHERE " +
           "(m.senderId = :userId1 AND m.recipientId = :userId2) OR " +
           "(m.senderId = :userId2 AND m.recipientId = :userId1) " +
           "ORDER BY m.sentAt DESC LIMIT :limit")
    List<Message> findLastMessagesBetween(
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2,
            @Param("limit") int limit);

    @Query("SELECT m FROM Message m WHERE " +
           "(m.senderId = :userId1 AND m.recipientId = :userId2) OR " +
           "(m.senderId = :userId2 AND m.recipientId = :userId1) " +
           "ORDER BY m.sentAt DESC")
    List<Message> findAllMessagesBetween(
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2);

    @Query("SELECT m FROM Message m WHERE " +
           "((m.senderId = :userId1 AND m.recipientId = :userId2) OR " +
           "(m.senderId = :userId2 AND m.recipientId = :userId1)) AND " +
           "LOWER(m.text) LIKE LOWER(CONCAT('%', :searchText, '%')) " +
           "ORDER BY m.sentAt DESC")
    List<Message> searchMessagesBetween(
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2,
            @Param("searchText") String searchText);
}
