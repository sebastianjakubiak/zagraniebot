package pl.zagranietyper.repository;

import pl.zagranietyper.config.AppConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {
    private final AppConfig config;

    public Database(AppConfig config) {
        this.config = config;
    }

    public Connection openConnection() throws SQLException {
        return DriverManager.getConnection(config.dbUrl(), config.dbUser(), config.dbPassword());
    }

    public void initializeSchema() {
        String sql = loadResource("/db/schema.sql");

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                for (String command : splitSql(sql)) {
                    if (!command.isBlank()) {
                        statement.execute(command);
                    }
                }
            }
            connection.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("Nie udało się zainicjalizować schematu DB", e);
        }
    }

    private static String loadResource(String path) {
        try (InputStream input = Database.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Brak resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Nie udało się odczytać resource: " + path, e);
        }
    }

    private static String[] splitSql(String sql) {
        return sql
                .replaceAll("(?m)^\\s*--.*$", "")
                .split(";");
    }
}
