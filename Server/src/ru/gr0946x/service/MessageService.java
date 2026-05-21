package ru.gr0946x.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import ru.gr0946x.db.DatabaseConfig;
import ru.gr0946x.entity.Message;

import java.util.List;

public class MessageService {
    private final EntityManagerFactory emf;

    public MessageService() {
        this.emf = DatabaseConfig.getInstance().getEntityManagerFactory();
    }

    public Message save(Message message) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            if (message.getId() == null) {
                em.persist(message);
            } else {
                message = em.merge(message);
            }
            em.getTransaction().commit();
            return message;
        } catch (Exception e) {
            em.getTransaction().rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    public List<Message> findLastMessagesBetween(Long userId1, Long userId2, int limit) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery(
                    "SELECT m FROM Message m WHERE " +
                    "(m.senderId = :userId1 AND m.recipientId = :userId2) OR " +
                    "(m.senderId = :userId2 AND m.recipientId = :userId1) " +
                    "ORDER BY m.sentAt ASC",
                    Message.class)
                    .setParameter("userId1", userId1)
                    .setParameter("userId2", userId2)
                    .setMaxResults(limit)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    public List<Message> findAllMessagesBetween(Long userId1, Long userId2) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery(
                    "SELECT m FROM Message m WHERE " +
                    "(m.senderId = :userId1 AND m.recipientId = :userId2) OR " +
                    "(m.senderId = :userId2 AND m.recipientId = :userId1) " +
                    "ORDER BY m.sentAt ASC",
                    Message.class)
                    .setParameter("userId1", userId1)
                    .setParameter("userId2", userId2)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    public List<Message> searchMessagesBetween(Long userId1, Long userId2, String searchText) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery(
                    "SELECT m FROM Message m WHERE " +
                    "((m.senderId = :userId1 AND m.recipientId = :userId2) OR " +
                    "(m.senderId = :userId2 AND m.recipientId = :userId1)) AND " +
                    "LOWER(m.text) LIKE LOWER(CONCAT('%', :searchText, '%')) " +
                    "ORDER BY m.sentAt DESC",
                    Message.class)
                    .setParameter("userId1", userId1)
                    .setParameter("userId2", userId2)
                    .setParameter("searchText", searchText)
                    .getResultList();
        } finally {
            em.close();
        }
    }
}
