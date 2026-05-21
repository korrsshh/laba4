package ru.gr0946x.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import ru.gr0946x.db.DatabaseConfig;
import ru.gr0946x.entity.User;

import java.util.List;
import java.util.Optional;

public class UserService {
    private final EntityManagerFactory emf;

    public UserService() {
        this.emf = DatabaseConfig.getInstance().getEntityManagerFactory();
    }

    public Optional<User> findByNick(String nick) {
        EntityManager em = emf.createEntityManager();
        try {
            List<User> users = em.createQuery(
                    "SELECT u FROM User u WHERE LOWER(u.nick) = LOWER(:nick)",
                    User.class)
                    .setParameter("nick", nick)
                    .getResultList();
            return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
        } finally {
            em.close();
        }
    }

    public boolean userExists(String nick) {
        return findByNick(nick).isPresent();
    }

    public User save(User user) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            if (user.getId() == null) {
                em.persist(user);
            } else {
                user = em.merge(user);
            }
            em.getTransaction().commit();
            return user;
        } catch (Exception e) {
            em.getTransaction().rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    public Optional<User> authenticate(String nick, String password) {
        Optional<User> user = findByNick(nick);
        if (user.isPresent() && user.get().getPassword().equals(password)) {
            return user;
        }
        return Optional.empty();
    }

    public User register(String nick, String password) throws Exception {
        if (nick == null || nick.trim().isEmpty()) {
            throw new Exception("Имя пользователя не может быть пустым");
        }
        
        if (!Character.isLetter(nick.charAt(0))) {
            throw new Exception("Имя должно начинаться с буквы");
        }

        if (userExists(nick)) {
            throw new Exception("Пользователь с таким именем уже существует");
        }

        User newUser = new User(nick, password);
        return save(newUser);
    }

    public List<User> findAll() {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery("SELECT u FROM User u", User.class).getResultList();
        } finally {
            em.close();
        }
    }

    public Optional<User> findById(Long id) {
        EntityManager em = emf.createEntityManager();
        try {
            User user = em.find(User.class, id);
            return Optional.ofNullable(user);
        } finally {
            em.close();
        }
    }
}

