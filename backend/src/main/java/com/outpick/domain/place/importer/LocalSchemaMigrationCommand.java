package com.outpick.domain.place.importer;

import org.flywaydb.core.Flyway;

import java.util.Set;

/** Explicit local-only migration command for the detached import workflow. */
public final class LocalSchemaMigrationCommand {
    private static String env(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " required");
        return value;
    }

    public static void main(String[] args) {
        String host = System.getenv().getOrDefault("MYSQL_HOST", "127.0.0.1");
        if (!Set.of("127.0.0.1", "localhost").contains(host))
            throw new IllegalArgumentException("Local DB only");
        String url = "jdbc:mysql://" + host + ":" + System.getenv().getOrDefault("MYSQL_PORT", "3306")
                + "/" + env("MYSQL_DATABASE");
        var result = Flyway.configure().dataSource(url, env("MYSQL_USER"), env("MYSQL_PASSWORD"))
                .locations("classpath:db/migration", "classpath:db/local").load().migrate();
        System.out.println("migrations_executed=" + result.migrationsExecuted);
    }
}
