package com.insurance.platform.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class Phase10MySqlMigrationTests {
    @Container
    static final MySQLContainer<?> EMPTY = mysql("phase10_empty");

    @Container
    static final MySQLContainer<?> UPGRADE = mysql("phase10_upgrade");

    @Test
    void emptyMySqlDatabaseMigratesThroughDocumentTable() throws Exception {
        Flyway flyway = flyway(EMPTY);
        assertThat(flyway.migrate().success).isTrue();

        try (var connection = DriverManager.getConnection(
                    EMPTY.getJdbcUrl(), EMPTY.getUsername(), EMPTY.getPassword());
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT COUNT(*) FROM information_schema.tables
                     WHERE table_schema = DATABASE()
                       AND table_name = 'iap_knowledge_document'
                     """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void existingPhase7SchemaUpgradesWithoutLosingUserData() throws Exception {
        Flyway phase7 = Flyway.configure()
                .dataSource(UPGRADE.getJdbcUrl(), UPGRADE.getUsername(), UPGRADE.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("1"))
                .load();
        assertThat(phase7.migrate().success).isTrue();

        UUID userId = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(
                    UPGRADE.getJdbcUrl(), UPGRADE.getUsername(), UPGRADE.getPassword());
             var insert = connection.prepareStatement("""
                     INSERT INTO iap_user
                         (user_id, username, password_hash, status, created_at, updated_at)
                     VALUES (?, 'upgrade-user', 'hash', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                     """)) {
            insert.setString(1, userId.toString());
            assertThat(insert.executeUpdate()).isEqualTo(1);
        }

        Flyway latest = flyway(UPGRADE);
        assertThat(latest.migrate().success).isTrue();
        try (var connection = DriverManager.getConnection(
                    UPGRADE.getJdbcUrl(), UPGRADE.getUsername(), UPGRADE.getPassword());
             var statement = connection.createStatement();
             var users = statement.executeQuery(
                     "SELECT user_id FROM iap_user WHERE username='upgrade-user'")) {
            assertThat(users.next()).isTrue();
            assertThat(users.getString(1)).isEqualTo(userId.toString());
        }
        try (var connection = DriverManager.getConnection(
                    UPGRADE.getJdbcUrl(), UPGRADE.getUsername(), UPGRADE.getPassword());
             var statement = connection.createStatement();
             var columns = statement.executeQuery("""
                     SELECT COUNT(*) FROM information_schema.columns
                     WHERE table_schema = DATABASE()
                       AND table_name = 'iap_knowledge_document'
                       AND column_name IN ('document_id', 'storage_key', 'index_status')
                     """)) {
            assertThat(columns.next()).isTrue();
            assertThat(columns.getInt(1)).isEqualTo(3);
        }
    }

    private static Flyway flyway(MySQLContainer<?> mysql) {
        return Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .load();
    }

    private static MySQLContainer<?> mysql(String database) {
        return new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
                .withDatabaseName(database)
                .withUsername("phase10_user")
                .withPassword(UUID.randomUUID().toString())
                .withEnv("MYSQL_ROOT_PASSWORD", UUID.randomUUID().toString())
                .withTmpFs(Map.of("/var/lib/mysql", "rw"))
                .withStartupTimeout(Duration.ofMinutes(5));
    }
}
