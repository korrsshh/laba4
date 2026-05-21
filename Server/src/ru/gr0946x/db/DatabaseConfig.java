package ru.gr0946x.db;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import ru.gr0946x.entity.Message;
import ru.gr0946x.entity.User;

public class DatabaseConfig {
    private static EntityManagerFactory emf;
    private static SessionFactory sessionFactory;
    private static DatabaseConfig instance;

    private DatabaseConfig() {
        try {
            // Пробуем загрузить H2 Driver
            try {
                Class.forName("org.h2.Driver");
                initializeWithH2();
            } catch (ClassNotFoundException e) {

                initializeWithDerby();
            }
            
            System.out.println("Hibernate инициализирован успешно");
        } catch (Exception e) {
            System.err.println("Ошибка инициализации БД: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Не удалось инициализировать EntityManagerFactory", e);
        }
    }

    private void initializeWithH2() {
        // Создаём ServiceRegistry с конфигурацией H2
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.H2Dialect")
                .applySetting("hibernate.connection.driver_class", "org.h2.Driver")
                .applySetting("hibernate.connection.url", "jdbc:h2:./chat_db;AUTO_SERVER=TRUE;MODE=MySQL;IFEXISTS=FALSE")
                .applySetting("hibernate.connection.user", "sa")
                .applySetting("hibernate.connection.password", "")
                .applySetting("hibernate.hbm2ddl.auto", "update")
                .applySetting("hibernate.show_sql", "false")
                .applySetting("hibernate.format_sql", "true")
                .build();
        
        try {
            // Создаём MetadataSources и добавляем entity классы
            MetadataSources sources = new MetadataSources(registry)
                    .addAnnotatedClass(User.class)
                    .addAnnotatedClass(Message.class);
            
            // Создаём SessionFactory
            sessionFactory = sources.buildMetadata().buildSessionFactory();
            
            // Получаем EntityManagerFactory
            emf = sessionFactory.unwrap(EntityManagerFactory.class);
        } catch (Exception e) {
            StandardServiceRegistryBuilder.destroy(registry);
            throw new RuntimeException("Ошибка инициализации H2", e);
        }
    }

    private void initializeWithDerby() {
        // Создаём ServiceRegistry с конфигурацией Derby
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.DerbyDialect")
                .applySetting("hibernate.connection.driver_class", "org.apache.derby.jdbc.EmbeddedDriver")
                .applySetting("hibernate.connection.url", "jdbc:derby:./chat_db;create=true")
                .applySetting("hibernate.connection.user", "")
                .applySetting("hibernate.connection.password", "")
                .applySetting("hibernate.hbm2ddl.auto", "update")
                .applySetting("hibernate.show_sql", "false")
                .applySetting("hibernate.format_sql", "true")
                .build();
        
        try {
            // Создаём MetadataSources и добавляем entity классы
            MetadataSources sources = new MetadataSources(registry)
                    .addAnnotatedClass(User.class)
                    .addAnnotatedClass(Message.class);
            
            // Создаём SessionFactory
            sessionFactory = sources.buildMetadata().buildSessionFactory();
            
            // Получаем EntityManagerFactory
            emf = sessionFactory.unwrap(EntityManagerFactory.class);
        } catch (Exception e) {
            StandardServiceRegistryBuilder.destroy(registry);
            throw new RuntimeException("Ошибка инициализации Derby", e);
        }
    }

    public static synchronized DatabaseConfig getInstance() {
        if (instance == null) {
            instance = new DatabaseConfig();
        }
        return instance;
    }

    public EntityManagerFactory getEntityManagerFactory() {
        if (emf == null) {
            throw new RuntimeException("EntityManagerFactory не инициализирована");
        }
        return emf;
    }

    public void shutdown() {
        if (sessionFactory != null && !sessionFactory.isClosed()) {
            sessionFactory.close();
        }
    }
}
