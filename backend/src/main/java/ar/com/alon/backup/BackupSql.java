package ar.com.alon.backup;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.*;

/**
 * Lee únicamente el subconjunto de SQL que produce el backup de Alon; nunca ejecuta DDL del ZIP.
 */
final class BackupSql {
    private static final Pattern CREATE =
            Pattern.compile("^CREATE TABLE `([A-Za-z0-9_]+)`\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern INSERT =
            Pattern.compile(
                    "^INSERT INTO `([A-Za-z0-9_]+)`(?:\\s*\\((`[A-Za-z0-9_]+`(?:\\s*,\\s*`[A-Za-z0-9_]+`)*)\\))?\\s+VALUES\\s*",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER =
            Pattern.compile("[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][-+]?[0-9]+)?");

    private static int literalEnd(String text, int i) {
        if (text.regionMatches(true, i, "_binary", 0, 7)) i = spaces(text, i + 7);
        if (i < text.length() && text.charAt(i) == '\'') {
            i++;
            while (i < text.length()) {
                char c = text.charAt(i++);
                if (c == '\\') {
                    if (i >= text.length()) throw invalid();
                    i++;
                } else if (c == '\'') {
                    if (i < text.length() && text.charAt(i) == '\'') i++;
                    else return i;
                }
            }
            throw invalid();
        }
        if (text.regionMatches(true, i, "NULL", 0, 4)) return i + 4;
        if (text.regionMatches(true, i, "0x", 0, 2)) {
            i += 2;
            while (i < text.length() && Character.digit(text.charAt(i), 16) >= 0) i++;
            return i;
        }
        var number = NUMBER.matcher(text).region(i, text.length());
        if (!number.lookingAt()) throw invalid();
        return number.end();
    }

    record Table(String ddl, int columns, List<String> writableColumns) {
        Table(String ddl, int columns) {
            this(ddl, columns, List.of());
        }
    }

    static Map<String, Table> schema(Connection connection) throws SQLException {
        var result = new TreeMap<String, Table>();
        try (var query =
                connection.prepareStatement(
                        "SELECT TABLE_NAME,ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_TYPE='BASE TABLE'")) {
            query.setString(1, connection.getCatalog());
            try (var rows = query.executeQuery()) {
                while (rows.next()) {
                    String name = rows.getString(1);
                    if (!"InnoDB".equalsIgnoreCase(rows.getString(2)))
                        throw new IllegalArgumentException(
                                "La restauración necesita tablas InnoDB.");
                    if (!name.matches("[A-Za-z0-9_]+"))
                        throw new IllegalArgumentException("Nombre de tabla no compatible.");
                    try (var stmt = connection.createStatement();
                            var ddl = stmt.executeQuery("SHOW CREATE TABLE `" + name + "`")) {
                        ddl.next();
                        int count = 0;
                        var writable = new ArrayList<String>();
                        try (var columnQuery =
                                connection.prepareStatement(
                                        "SELECT COLUMN_NAME,GENERATION_EXPRESSION FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME=? ORDER BY ORDINAL_POSITION")) {
                            columnQuery.setString(1, connection.getCatalog());
                            columnQuery.setString(2, name);
                            try (var columns = columnQuery.executeQuery()) {
                                while (columns.next()) {
                                    count++;
                                    String expression = columns.getString(2);
                                    if (expression == null || expression.isBlank())
                                        writable.add(columns.getString(1));
                                }
                            }
                        }
                        result.put(name, new Table(normalize(ddl.getString(2)), count, writable));
                    }
                }
            }
        }
        return result;
    }

    static String normalize(String ddl) {
        return ddl.replaceAll("(?i)\\sAUTO_INCREMENT=[0-9]+", "").replaceAll("\\s+", " ").trim();
    }

    static void validate(Path sql, Map<String, Table> schema) throws IOException {
        if (schema.isEmpty())
            throw new IllegalArgumentException("No se encontró el esquema de Alon.");
        var created = new HashSet<String>();
        statements(
                sql,
                statement -> {
                    var create = CREATE.matcher(statement);
                    if (create.find()) {
                        String name = create.group(1);
                        Table table = schema.get(name);
                        if (table == null
                                || !table.ddl().equals(normalize(statement))
                                || !created.add(name))
                            throw new IllegalArgumentException(
                                    "El backup no es compatible con la estructura de esta versión de Alon.");
                        return;
                    }
                    var insert = INSERT.matcher(statement);
                    if (insert.find()) {
                        Table table = schema.get(insert.group(1));
                        if (table == null || !created.contains(insert.group(1))) throw invalid();
                        int expected = table.columns();
                        if (insert.group(2) != null) {
                            var columns =
                                    Arrays.stream(insert.group(2).replace("`", "").split(","))
                                            .map(String::trim)
                                            .toList();
                            if (!columns.equals(table.writableColumns())) throw invalid();
                            expected = columns.size();
                        } else if (!table.writableColumns().isEmpty()
                                && table.writableColumns().size() != table.columns())
                            throw invalid();
                        validateValues(statement, insert.end(), expected);
                        return;
                    }
                    // Se reconocen pero no se ejecutan instrucciones de configuración ni de
                    // estructura.
                    if (statement.matches("(?is)^DROP TABLE IF EXISTS `[A-Za-z0-9_]+`$")
                            || statement.matches("(?is)^LOCK TABLES `[A-Za-z0-9_]+` WRITE$")
                            || statement.equalsIgnoreCase("UNLOCK TABLES")
                            || statement.matches(
                                    "(?is)^ALTER TABLE `[A-Za-z0-9_]+` (?:DISABLE|ENABLE) KEYS$")
                            || statement.matches("(?is)^(?:SET\\s|USE\\s|CREATE DATABASE\\s).+"))
                        return;
                    throw invalid();
                });
        if (!created.equals(schema.keySet()))
            throw new IllegalArgumentException(
                    "El backup está incompleto: faltan tablas del sistema.");
        validateEnvelope(sql);
    }

    private static void validateEnvelope(Path sql) throws IOException {
        try (var channel = Files.newByteChannel(sql)) {
            var head = java.nio.ByteBuffer.allocate(2048);
            channel.read(head);
            var tail = java.nio.ByteBuffer.allocate(4096);
            channel.position(Math.max(0, channel.size() - 4096));
            channel.read(tail);
            String first = new String(head.array(), 0, head.position(), StandardCharsets.UTF_8);
            String last = new String(tail.array(), 0, tail.position(), StandardCharsets.UTF_8);
            if (!first.contains("-- MySQL dump") || !last.contains("-- Dump completed on "))
                throw new IllegalArgumentException(
                        "El backup SQL está incompleto o no fue generado por Alon.");
        }
    }

    static void apply(Path sql, Connection connection) throws IOException, SQLException {
        try {
            statements(
                    sql,
                    text -> {
                        var insert = INSERT.matcher(text);
                        if (insert.find() && !insert.group(1).equals("flyway_schema_history")) {
                            try (var stmt = connection.createStatement()) {
                                stmt.executeUpdate(text);
                            } catch (SQLException e) {
                                throw new SqlFailure(e);
                            }
                        }
                    });
        } catch (SqlFailure e) {
            throw e.failure;
        }
    }

    private static class SqlFailure extends RuntimeException {
        final SQLException failure;

        SqlFailure(SQLException failure) {
            super(failure);
            this.failure = failure;
        }
    }

    private static int spaces(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return i;
    }

    static void validateValues(String text, int offset, int columns) {
        int i = spaces(text, offset);
        while (true) {
            if (i >= text.length() || text.charAt(i++) != '(') throw invalid();
            for (int column = 0; column < columns; column++) {
                i = spaces(text, i);
                i = spaces(text, literalEnd(text, i));
                char separator = column == columns - 1 ? ')' : ',';
                if (i >= text.length() || text.charAt(i++) != separator) throw invalid();
            }
            i = spaces(text, i);
            if (i == text.length()) return;
            if (text.charAt(i++) != ',') throw invalid();
            i = spaces(text, i);
        }
    }

    static IllegalArgumentException invalid() {
        return new IllegalArgumentException(
                "El SQL no corresponde a un backup completo generado por Alon.");
    }

    static void statements(Path path, Consumer<String> consume) throws IOException {
        try (var reader =
                new PushbackReader(Files.newBufferedReader(path, StandardCharsets.UTF_8), 3)) {
            var statement = new StringBuilder();
            int quote = 0, c;
            while ((c = reader.read()) != -1) {
                if (c == 0xfeff && statement.isEmpty()) continue;
                if (quote != 0) {
                    statement.append((char) c);
                    if (c == '\\') {
                        int next = reader.read();
                        if (next < 0) throw invalid();
                        statement.append((char) next);
                    } else if (c == quote) quote = 0;
                } else if (c == '\'' || c == '"' || c == '`') {
                    quote = c;
                    statement.append((char) c);
                } else if (c == ';') {
                    String text = statement.toString().trim();
                    if (!text.isEmpty()) consume.accept(text);
                    statement.setLength(0);
                } else if (c == '#') {
                    while ((c = reader.read()) != -1 && c != '\n') {}
                    statement.append(' ');
                } else if (c == '-') {
                    int next = reader.read(), after = reader.read();
                    if (next == '-' && (after < 0 || Character.isWhitespace(after))) {
                        if (after != '\n' && after >= 0)
                            while ((c = reader.read()) != -1 && c != '\n') {}
                        statement.append(' ');
                    } else {
                        if (after >= 0) reader.unread(after);
                        if (next >= 0) reader.unread(next);
                        statement.append('-');
                    }
                } else if (c == '/') {
                    int next = reader.read();
                    if (next == '*') {
                        int previous = 0;
                        while ((c = reader.read()) != -1 && !(previous == '*' && c == '/'))
                            previous = c;
                        if (c < 0) throw invalid();
                        statement.append(' ');
                    } else {
                        if (next >= 0) reader.unread(next);
                        statement.append('/');
                    }
                } else statement.append((char) c);
                if (statement.length() > 32 * 1024 * 1024)
                    throw new IllegalArgumentException(
                            "El backup tiene una sentencia demasiado grande.");
            }
            if (quote != 0 || !statement.toString().isBlank())
                throw new IllegalArgumentException("El backup SQL está truncado o incompleto.");
        }
    }
}
