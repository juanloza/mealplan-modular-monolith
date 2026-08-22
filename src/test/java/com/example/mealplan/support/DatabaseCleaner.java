package com.example.mealplan.support;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * What isolates one integration test from the next.
 *
 * <p>No integration test is annotated with {@code @Transactional}: rolling back at the end of each
 * one is convenient but hides exactly what this application is meant to demonstrate, because check
 * constraints, foreign keys and optimistic locking all show up at commit, and with the test holding
 * a transaction open there would be no two transactions able to compete.
 *
 * <p>An ordinary component, because it is wanted in every context.
 */
@Component
public class DatabaseCleaner {

    private static final String TABLES_QUERY = """
            select table_name
              from information_schema.tables
             where table_schema = 'public'
               and table_type = 'BASE TABLE'
               and table_name <> 'flyway_schema_history'
            """;

    private final JdbcTemplate jdbc;

    private List<String> tables;

    public DatabaseCleaner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The list is derived from the catalogue rather than written by hand: a table added later would
     * otherwise go unclean and produce intermittent failures nobody can attribute.
     */
    public void clean() {
        if (tables == null) {
            tables = jdbc.queryForList(TABLES_QUERY, String.class);
        }
        jdbc.execute("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
    }
}
