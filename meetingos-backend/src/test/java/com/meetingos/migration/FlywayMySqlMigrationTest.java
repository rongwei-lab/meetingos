package com.meetingos.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "MEETINGOS_MYSQL_MIGRATION_TEST_URL", matches = ".+")
class FlywayMySqlMigrationTest {

    private static final int PRODUCTION_V1_CHECKSUM = -317070242;

    @Test
    void migratesAnEmptyMySqlDatabaseFromV1ToLatest() throws SQLException {
        String url = requiredEnvironmentVariable("MEETINGOS_MYSQL_MIGRATION_TEST_URL");
        String username = requiredEnvironmentVariable("MEETINGOS_MYSQL_MIGRATION_TEST_USERNAME");
        String password = requiredEnvironmentVariable("MEETINGOS_MYSQL_MIGRATION_TEST_PASSWORD");

        // 该测试只连接 CI 创建的一次性空库，不调用 clean/repair，避免掩盖真实升级问题。
        assertDatabaseIsEmpty(url, username, password);
        Flyway flyway = Flyway.configure()
            .dataSource(url, username, password)
            .locations("classpath:db/migration")
            .cleanDisabled(true)
            .load();

        flyway.migrate();
        flyway.validate();

        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                 "select version, checksum, success from flyway_schema_history " +
                     "where type = 'SQL' order by installed_rank")) {
            List<String> versions = new ArrayList<>();
            Integer v1Checksum = null;
            while (rows.next()) {
                String version = rows.getString("version");
                versions.add(version);
                assertTrue(rows.getBoolean("success"), "迁移 V" + version + " 未成功完成");
                if ("1".equals(version)) {
                    v1Checksum = rows.getInt("checksum");
                }
            }

            assertEquals(List.of("1", "2", "3", "4", "5"), versions,
                "CI 必须从 V1 完整迁移到仓库当前最新版本");
            assertEquals(PRODUCTION_V1_CHECKSUM, v1Checksum,
                "V1 必须与生产库已经记录的 Flyway checksum 完全一致");
        }
    }

    private void assertDatabaseIsEmpty(String url, String username, String password) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery(
                 "select count(*) from information_schema.tables where table_schema = database()")) {
            assertTrue(row.next());
            assertEquals(0, row.getInt(1),
                "迁移测试只允许使用一次性空库，拒绝修改已有数据库");
        }
    }

    private String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        assertTrue(value != null && !value.isBlank(), "缺少环境变量: " + name);
        return value;
    }
}
