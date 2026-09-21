package com.resourceapp.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates and connects to a separate physical MySQL database for each resource person.
 *
 * The main "resource_db" (configured in application.properties) only ever holds the
 * RESOURCES and TASKS tables. Every resource person's assignments instead live in their
 * own database, named from their Name ID, so one person's data can never show up while
 * another person is selected on the User page - it's a different database entirely.
 */
@Service
public class PersonDatabaseService {

    // Base server URL with NO database name in it, e.g. jdbc:mysql://localhost:3306
    @Value("${app.mysql.server-url}")
    private String serverUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    // We create databases/tables ourselves with explicit SQL below, so we don't rely on the
    // driver's own createDatabaseIfNotExist feature (which requires a database name in the
    // URL and can fail on a connection that has none, like the server-level one below).
    // allowPublicKeyRetrieval avoids a common MySQL 8 "Public Key Retrieval is not allowed"
    // connection failure when using caching_sha2_password with useSSL=false.
    private static final String CONNECT_PARAMS =
            "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    /**
     * Turns a resource's Name ID into a safe, unique database name, e.g.
     * "R-1001" -> "resource_person_r_1001".
     */
    public String buildDbNameFromNameId(String nameId) {
        String sanitized = nameId.trim().toLowerCase().replaceAll("[^a-z0-9]+", "_");
        return "resource_person_" + sanitized;
    }

    /**
     * Creates the person's database (if it doesn't already exist) and their ASSIGNMENTS
     * table inside it. Safe to call more than once - both statements are idempotent.
     */
    public void provisionDatabaseFor(String dbName) {
        // NOTE: the "/" here is required - "jdbc:mysql://host:port?params" (no slash) is an
        // invalid URL for the MySQL driver and can fail or misbehave silently.
        try (Connection serverConn = DriverManager.getConnection(serverUrl + "/" + CONNECT_PARAMS, username, password);
             Statement st = serverConn.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + dbName + "`");
        } catch (SQLException e) {
            throw new RuntimeException("Could not create database for resource: " + dbName, e);
        }

        try (Connection dbConn = getConnectionFor(dbName);
             Statement st = dbConn.createStatement()) {
            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS ASSIGNMENTS (" +
                            "ID BIGINT AUTO_INCREMENT PRIMARY KEY," +
                            "TASK_NAME VARCHAR(150) NOT NULL," +
                            "TASK_CODE VARCHAR(50) NOT NULL," +
                            "ASSIGNMENT_DATE VARCHAR(10)," +
                            "MONTH VARCHAR(20) NOT NULL," +
                            "YEAR INT NOT NULL," +
                            "HOURS INT," +
                            "REMARKS TEXT" +
                            ")"
            );
        } catch (SQLException e) {
            throw new RuntimeException("Could not create ASSIGNMENTS table for database: " + dbName, e);
        }
    }

    /** Opens a fresh JDBC connection straight to one person's database. Caller must close it. */
    public Connection getConnectionFor(String dbName) throws SQLException {
        String url = serverUrl + "/" + dbName + CONNECT_PARAMS;
        return DriverManager.getConnection(url, username, password);
    }
}
