package tino.nucleo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * O Postgres do {@code docker-compose.yml} (no CI, o service do job test, na mesma porta),
 * num banco {@code tino_teste} à parte: os testes apagam tudo, e o banco {@code tino} fica
 * para rodar o bot local. Cada teste começa com o banco migrado e vazio, e com as contas
 * 111, 222 e 333.
 */
final class BancoDeTeste {

    private static final String SERVIDOR = "jdbc:postgresql://localhost:5433/";
    static final String URL = SERVIDOR + "tino_teste";
    static final String USUARIO = "tino";
    static final String SENHA = "tino";

    private static boolean criado;

    private BancoDeTeste() {}

    static Connection limpo() throws SQLException {
        criarBanco();
        Tino.migrar(URL, USUARIO, SENHA);
        Connection c = DriverManager.getConnection(URL, USUARIO, SENHA);
        try (Statement s = c.createStatement()) {
            s.execute("""
                    TRUNCATE conta, identidade, categoria, lancamento, recorrente, pendente
                    RESTART IDENTITY CASCADE""");
            s.execute("INSERT INTO conta (id) OVERRIDING SYSTEM VALUE VALUES (111), (222), (333)");
        }
        return c;
    }

    private static void criarBanco() throws SQLException {
        if (criado) {
            return;
        }
        try (Connection c = DriverManager.getConnection(SERVIDOR + "tino", USUARIO, SENHA);
             Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE tino_teste");
        } catch (SQLException e) {
            if (!"42P04".equals(e.getSQLState())) {   // 42P04: o banco já existe
                throw e;
            }
        }
        criado = true;
    }

    /** Volta o banco a vazio, sem esquema nenhum: o próximo {@link #limpo} migra de novo. */
    static void semEsquema() throws SQLException {
        criarBanco();
        try (Connection c = DriverManager.getConnection(URL, USUARIO, SENHA);
             Statement s = c.createStatement()) {
            s.execute("DROP SCHEMA public CASCADE");
            s.execute("CREATE SCHEMA public");
        }
    }
}
