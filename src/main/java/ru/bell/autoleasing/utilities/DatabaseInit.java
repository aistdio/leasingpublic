package ru.bell.autoleasing.utilities;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.Properties;

@Slf4j
public class DatabaseInit {
    private static final String PROPERTIES_FILE = "application.yml";

    private static boolean toInit;
    private static String adminUrl;
    private static String adminName;
    private static String adminPass;
    private static String dbName;

    public static void createDatabaseIfNotExists() {
        loadProperties();

        if (toInit) {
            try (Connection conn = DriverManager.getConnection(adminUrl, adminName, adminPass);
                 Statement stmt = conn.createStatement()) {

                String checkQuery = "SELECT 1 FROM pg_database WHERE datname = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(checkQuery)) {
                    pstmt.setString(1, dbName);
                    ResultSet rs = pstmt.executeQuery();

                    if (!rs.next()) {
                        stmt.executeUpdate("CREATE DATABASE " + dbName);
                        System.out.println("База данных " + dbName + " была создана");
                    } else {
                        System.out.println("База данных " + dbName + " уже существует");
                    }
                }

            } catch (SQLException e) {
                log.error("Ошибка при создании базы данных {}", dbName, e);
                throw new RuntimeException("Не удалось создать базу данных " + dbName, e);
            }
        }
    }

    private static void loadProperties() {
        Properties props = new Properties();

        try (InputStream input = DatabaseInit.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (input == null) {
                throw new IllegalStateException("Файл " + PROPERTIES_FILE + " не найден в classpath при инициализации базы данных");
            }

            props.load(input);

            toInit = Boolean.parseBoolean(props.getProperty("main.db.autoinit", "false"));

            if (toInit) {
                String dbUrl = props.getProperty("spring.datasource.url");
                int lastSlashIndex = dbUrl.lastIndexOf("/");

                adminUrl = dbUrl.substring(0, lastSlashIndex) + "/postgres";
                dbName = dbUrl.substring(lastSlashIndex + 1);

                adminName = props.getProperty("spring.datasource.username");
                adminPass = props.getProperty("spring.datasource.password");

                if (adminUrl == null || adminName == null || adminPass == null || dbName == null) {
                    throw new IllegalArgumentException("Некоторые конфигурации в " + PROPERTIES_FILE + " отсутствуют");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при загрузке файла " + PROPERTIES_FILE, e);
        }
    }
}
