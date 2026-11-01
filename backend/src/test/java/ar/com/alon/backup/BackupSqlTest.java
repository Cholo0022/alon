package ar.com.alon.backup;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackupSqlTest {
    @TempDir Path temp;

    @Test
    void acceptsGeneratedDumpAndQuotedSemicolonsButNeverExecutesDdl() throws Exception {
        Path sql = temp.resolve("database.sql");
        Files.writeString(
                sql,
                "-- MySQL dump\n--\nCREATE DATABASE `other`; USE `other`;\nDROP TABLE IF EXISTS `items`;\n--\nCREATE TABLE `items` (`id` int, `value` text) ENGINE=InnoDB AUTO_INCREMENT=9;\n/*!40101 SET something=1 */;\nINSERT INTO `items` VALUES (1,'a; b \\\' c'),(2,'x-- y /* z */');\n-- Dump completed on 2026-11-01 12:00:00\n");
        var schema =
                Map.of(
                        "items",
                        new BackupSql.Table(
                                "CREATE TABLE `items` (`id` int, `value` text) ENGINE=InnoDB", 2));
        assertThatCode(() -> BackupSql.validate(sql, schema)).doesNotThrowAnyException();
    }

    @Test
    void rejectsExpressionsShellCommandsOtherTablesAndTruncation() throws Exception {
        var schema =
                Map.of(
                        "items",
                        new BackupSql.Table("CREATE TABLE `items` (`id` int, `value` text)", 2));
        for (String tail :
                List.of(
                        "INSERT INTO `items` VALUES (1,LOAD_FILE('/etc/passwd'));",
                        "INSERT INTO `elsewhere` VALUES (1,'x');",
                        "\\! calc.exe;",
                        "INSERT INTO `items` VALUES (1,'unfinished",
                        "INSERT INTO `items` VALUES (1,'x'); DROP DATABASE other;")) {
            Path sql = temp.resolve("bad.sql");
            Files.writeString(sql, "CREATE TABLE `items` (`id` int, `value` text);" + tail);
            assertThatThrownBy(() -> BackupSql.validate(sql, schema))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsMissingOrDifferentTables() throws Exception {
        Path sql = temp.resolve("bad.sql");
        Files.writeString(sql, "CREATE TABLE `items` (`id` int);");
        assertThatThrownBy(
                        () ->
                                BackupSql.validate(
                                        sql,
                                        Map.of(
                                                "items",
                                                new BackupSql.Table(
                                                        "CREATE TABLE `items` (`id` bigint)", 1))))
                .hasMessageContaining("compatible");
        Files.writeString(sql, "-- no data\n");
        assertThatThrownBy(
                        () ->
                                BackupSql.validate(
                                        sql,
                                        Map.of(
                                                "items",
                                                new BackupSql.Table(
                                                        "CREATE TABLE `items` (`id` int)", 1))))
                .hasMessageContaining("incompleto");
    }

    @Test
    void rejectsWrongColumnCountsAndAcceptsBinaryNullAndEscapes() {
        assertThatCode(
                        () ->
                                BackupSql.validateValues(
                                        "(0x01,NULL,'line\\nquote\\\'end'),(0x02,-1.20e+3,_binary 'data')",
                                        0,
                                        3))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> BackupSql.validateValues("(1,'x',2)", 0, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
