package tino.nucleo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * O Postgres do {@code docker-compose.yml} (no CI, o service do job test, na mesma porta).
 * Cada teste começa com o banco migrado e vazio, e com as contas 111, 222 e 333.
 */
final class BancoDeTeste {

    static final String URL = "jdbc:postgresql://localhost:5433/tino";
    static final String USUARIO = "tino";
    static final String SENHA = "tino";

    private BancoDeTeste() {}

    static Connection limpo() throws SQLException {
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

    /** Volta o banco a vazio, sem esquema nenhum: o próximo {@link #limpo} migra de novo. */
    static void semEsquema() throws SQLException {
        try (Connection c = DriverManager.getConnection(URL, USUARIO, SENHA);
             Statement s = c.createStatement()) {
            s.execute("DROP SCHEMA public CASCADE");
            s.execute("CREATE SCHEMA public");
        }
    }
}
