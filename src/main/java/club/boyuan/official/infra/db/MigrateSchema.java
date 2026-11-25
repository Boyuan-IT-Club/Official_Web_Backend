package club.boyuan.official.infra.db;

import org.flywaydb.core.Flyway;

/** Standalone deployment entry point. This process has no web, queue or mail credentials. */
public final class MigrateSchema {
    private MigrateSchema() {}

    public static void main(String[] args) {
        Flyway.configure()
                .dataSource(required("MIGRATION_URL"), required("MIGRATION_USER"), required("MIGRATION_PASSWORD"))
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .validateOnMigrate(true)
                .load()
                .migrate();
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing " + name);
        return value;
    }
}
