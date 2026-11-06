package tino.nucleo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Locale;

/** Regras e consultas. Todo método recebe a conexão e filtra por usuário. */
final class Financas {

    static final String GASTO = "gasto";
    static final String GANHO = "ganho";

    static final int MAX_RAIZ = 10;
    static final int MAX_SUB = 20;
    static final int DIAS_DO_NORMAL = 90;

    private static final List<String> PADRAO_GASTO = List.of(
            "Alimentação", "Transporte", "Moradia", "Saúde", "Lazer", "Educação", "Outros");
    private static final List<String> PADRAO_GANHO = List.of(
            "Salário", "Freelance", "Investimentos", "Outros");

    private Financas() {}

    // ------------------------------------------------------------- estrutura

    static void criarTabelas(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("""
                    CREATE TABLE IF NOT EXISTS categoria (
                        id      INTEGER PRIMARY KEY,
                        usuario TEXT    NOT NULL,
                        pai_id  INTEGER REFERENCES categoria(id),
                        tipo    TEXT    NOT NULL,
                        nome    TEXT    NOT NULL
                    )""");
            s.execute("""
                    CREATE TABLE IF NOT EXISTS lancamento (
                        id             INTEGER PRIMARY KEY,
                        usuario        TEXT    NOT NULL,
                        valor_centavos INTEGER NOT NULL,
                        categoria_id   INTEGER NOT NULL REFERENCES categoria(id),
                        descricao      TEXT,
                        data           TEXT    NOT NULL
                    )""");
            s.execute("""
                    CREATE TABLE IF NOT EXISTS recorrente (
                        id             INTEGER PRIMARY KEY,
                        usuario        TEXT    NOT NULL,
                        valor_centavos INTEGER NOT NULL,
                        categoria_id   INTEGER NOT NULL REFERENCES categoria(id),
                        descricao      TEXT
                    )""");
            s.execute("CREATE INDEX IF NOT EXISTS idx_consulta ON lancamento (usuario, data)");
            // A coluna usuario guarda o id da conta. Cada canal (Discord, Google, Telegram)
            // é uma identidade que aponta para ela, então uma conta tem várias entradas.
            s.execute("CREATE TABLE IF NOT EXISTS conta (id INTEGER PRIMARY KEY)");
            s.execute("""
                    CREATE TABLE IF NOT EXISTS identidade (
                        provedor TEXT    NOT NULL,
                        externo  TEXT    NOT NULL,
                        conta_id INTEGER NOT NULL REFERENCES conta(id),
                        PRIMARY KEY (provedor, externo)
                    )""");
            s.execute("""
                    CREATE TABLE IF NOT EXISTS pendente (
                        chave          TEXT    PRIMARY KEY,
                        usuario        TEXT    NOT NULL,
                        valor_centavos INTEGER NOT NULL,
                        categoria      TEXT,
                        descricao      TEXT,
                        data           TEXT    NOT NULL,
                        criado_em      TEXT    NOT NULL
                    )""");
        }
    }

    /**
     * Cria as categorias iniciais na primeira vez que o usuário aparece.
     * São dele, e não do sistema, justamente para que possa renomear e excluir.
     *
     * @return true na primeira vez, para quem chama poder dar as boas-vindas
     */
    static boolean prepararUsuario(Connection c, String usuario) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM categoria WHERE usuario = ?")) {
            ps.setString(1, usuario);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return false;
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO categoria (usuario, pai_id, tipo, nome) VALUES (?, NULL, ?, ?)")) {
            for (String nome : PADRAO_GASTO) {
                ps.setString(1, usuario);
                ps.setString(2, GASTO);
                ps.setString(3, nome);
                ps.addBatch();
            }
            for (String nome : PADRAO_GANHO) {
                ps.setString(1, usuario);
                ps.setString(2, GANHO);
                ps.setString(3, nome);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        return true;
    }

    // ---------------------------------------------------------------- contas

    /** Conta dona desta identidade, criada no primeiro contato. Devolve o id usado em {@code usuario}. */
    static String conta(Connection c, String provedor, String externo) throws SQLException {
        Optional<String> existente = contaDa(c, provedor, externo);
        if (existente.isPresent()) {
            return existente.get();
        }
        long id;
        try (Statement s = c.createStatement()) {
            s.execute("INSERT INTO conta DEFAULT VALUES");
            try (ResultSet rs = s.executeQuery("SELECT last_insert_rowid()")) {
                rs.next();
                id = rs.getLong(1);
            }
        }
        inserirIdentidade(c, provedor, externo, id);
        return String.valueOf(id);
    }

    /** Liga mais uma identidade a uma conta existente: entrar pelo Google e ver o que lançou pelo Discord. */
    static void vincular(Connection c, String conta, String provedor, String externo)
            throws SQLException {
        Optional<String> dona = contaDa(c, provedor, externo);
        if (dona.isPresent() && !dona.get().equals(conta)) {
            // ponytail: sem fusão de contas; se as duas tiverem dados, quem chama decide.
            throw new IllegalArgumentException("essa conta de " + provedor + " já está ligada a outro usuário");
        }
        if (dona.isEmpty()) {
            inserirIdentidade(c, provedor, externo, Long.parseLong(conta));
        }
    }

    private static Optional<String> contaDa(Connection c, String provedor, String externo)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT conta_id FROM identidade WHERE provedor = ? AND externo = ?")) {
            ps.setString(1, provedor);
            ps.setString(2, externo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        }
    }

    private static void inserirIdentidade(Connection c, String provedor, String externo, long conta)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO identidade (provedor, externo, conta_id) VALUES (?, ?, ?)")) {
            ps.setString(1, provedor);
            ps.setString(2, externo);
            ps.setLong(3, conta);
            ps.executeUpdate();
        }
    }

    /**
     * Antes das contas, {@code usuario} guardava o id do Discord. Roda no boot e troca
     * cada um pela conta correspondente. Idempotente: id de conta é pequeno, snowflake
     * do Discord tem 17+ dígitos, então o que já é conta não volta a ser migrado.
     */
    static void migrarParaContas(Connection c) throws SQLException {
        List<String> antigos = new ArrayList<>();
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("""
                     SELECT usuario FROM categoria
                     UNION SELECT usuario FROM lancamento
                     UNION SELECT usuario FROM recorrente
                     EXCEPT SELECT CAST(id AS TEXT) FROM conta""")) {
            while (rs.next()) {
                antigos.add(rs.getString(1));
            }
        }
        if (antigos.isEmpty()) {
            return;
        }
        c.setAutoCommit(false);
        try {
            for (String discord : antigos) {
                String conta = conta(c, "discord", discord);
                for (String tabela : List.of("categoria", "lancamento", "recorrente")) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE " + tabela + " SET usuario = ? WHERE usuario = ?")) {
                        ps.setString(1, conta);
                        ps.setString(2, discord);
                        ps.executeUpdate();
                    }
                }
            }
            c.commit();
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(true);
        }
    }

    // ------------------------------------------------------------ categorias

    /**
     * Resolve "Alimentação > Mercado" para o id da folha, criando o que faltar.
     * Criar sob demanda dispensa cadastro prévio; os limites evitam a lista virar lixo.
     */
    static long categoriaId(Connection c, String usuario, String caminho, String tipo)
            throws SQLException {
        String[] partes = separar(caminho);
        Long pai = null;
        for (String nome : partes) {
            pai = acharOuCriar(c, usuario, nome, pai, tipo);
        }
        return pai;
    }

    private static String[] separar(String caminho) {
        if (caminho == null || caminho.isBlank()) {
            throw new IllegalArgumentException("falta a categoria: escreva por exemplo `Mercado`");
        }
        String[] partes = caminho.split(">");
        if (partes.length > 2) {
            throw new IllegalArgumentException("são no máximo dois níveis: use `Categoria > Subcategoria`");
        }
        for (int i = 0; i < partes.length; i++) {
            partes[i] = Entrada.capitalizar(partes[i]);
            if (partes[i].isEmpty()) {
                throw new IllegalArgumentException("categoria inválida `%s`: use `Categoria` ou `Categoria > Subcategoria`".formatted(caminho));
            }
            if (partes[i].length() > 30) {
                throw new IllegalArgumentException("nome muito longo `%s`: são até 30 caracteres".formatted(partes[i]));
            }
        }
        return partes;
    }

    /**
     * Deixa as categorias já gravadas na forma canônica. Roda no boot; sem isso,
     * quem usou o bot antes da normalização veria "mercado" e "Mercado" na lista.
     */
    static void normalizarCategorias(Connection c) throws SQLException {
        record Ajuste(long id, String nome) {}
        List<Ajuste> ajustes = new ArrayList<>();

        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, nome FROM categoria")) {
            while (rs.next()) {
                String canonico = Entrada.capitalizar(rs.getString(2));
                if (!canonico.equals(rs.getString(2))) {
                    ajustes.add(new Ajuste(rs.getLong(1), canonico));
                }
            }
        }
        if (ajustes.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE categoria SET nome = ? WHERE id = ?")) {
            for (Ajuste a : ajustes) {
                ps.setString(1, a.nome());
                ps.setLong(2, a.id());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // Os nomes são gravados já normalizados, então a comparação bate exata.
    // O COLLATE NOCASE fica como rede para linhas gravadas antes disso.
    private static Long buscar(Connection c, String usuario, String nome, Long pai, String tipo)
            throws SQLException {
        String sql = """
                SELECT id FROM categoria
                 WHERE usuario = ? AND tipo = ? AND nome = ? COLLATE NOCASE
                   AND ((? IS NULL AND pai_id IS NULL) OR pai_id = ?)
                 LIMIT 1""";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, usuario);
            ps.setString(2, tipo);
            ps.setString(3, nome);
            ps.setObject(4, pai);
            ps.setObject(5, pai);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private static Long acharOuCriar(Connection c, String usuario, String nome, Long pai, String tipo)
            throws SQLException {
        Long existente = buscar(c, usuario, nome, pai, tipo);
        if (existente != null) {
            return existente;
        }

        boolean raiz = pai == null;
        int quantas = contar(c, usuario, tipo, raiz);
        if (raiz && quantas >= MAX_RAIZ) {
            throw new IllegalArgumentException(
                    "limite de %d categorias de %s atingido — exclua ou renomeie alguma com `/categoria`"
                            .formatted(MAX_RAIZ, tipo));
        }
        if (!raiz && quantas >= MAX_SUB) {
            throw new IllegalArgumentException(
                    "limite de %d subcategorias de %s atingido — exclua ou renomeie alguma com `/categoria`"
                            .formatted(MAX_SUB, tipo));
        }

        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO categoria (usuario, pai_id, tipo, nome) VALUES (?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, usuario);
            ps.setObject(2, pai);
            ps.setString(3, tipo);
            ps.setString(4, nome);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    static int contar(Connection c, String usuario, String tipo, boolean raiz) throws SQLException {
        String sql = "SELECT COUNT(*) FROM categoria WHERE usuario = ? AND tipo = ? AND pai_id IS "
                + (raiz ? "NULL" : "NOT NULL");
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, usuario);
            ps.setString(2, tipo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Caminhos completos de um tipo. Passe um limite para o autocomplete (o Discord aceita 25). */
    static List<String> caminhos(Connection c, String usuario, String tipo, String filtro, int limite)
            throws SQLException {
        String sql = """
                SELECT caminho FROM (
                    SELECT COALESCE(p.nome || ' > ', '') || c.nome AS caminho,
                           c.usuario AS dono, c.tipo AS t
                      FROM categoria c LEFT JOIN categoria p ON p.id = c.pai_id
                )
                 WHERE dono = ? AND (? IS NULL OR t = ?) AND caminho LIKE ?
                 ORDER BY caminho LIMIT ?""";
        List<String> nomes = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, usuario);
            ps.setString(2, tipo);
            ps.setString(3, tipo);
            ps.setString(4, "%" + (filtro == null ? "" : filtro) + "%");
            ps.setInt(5, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    nomes.add(rs.getString(1));
                }
            }
        }
        return nomes;
    }

    /** Id de uma categoria que já existe. Lança se não encontrar. */
    private static long exigir(Connection c, String usuario, String caminho, String tipo)
            throws SQLException {
        String[] partes = separar(caminho);
        Long id = null;
        for (String nome : partes) {
            id = buscar(c, usuario, nome, id, tipo);
            if (id == null) {
                throw new IllegalArgumentException(parecida(c, usuario, nome, tipo)
                        .map(s -> "não achei `%s` — você quis dizer `%s`?".formatted(caminho, s))
                        .orElseGet(() -> "não achei a categoria `%s`".formatted(caminho)));
            }
        }
        return id;
    }

    static void renomear(Connection c, String usuario, String caminho, String novoNome, String tipo)
            throws SQLException {
        String nome = novoNome == null ? "" : novoNome.trim();
        if (nome.isEmpty() || nome.length() > 30) {
            throw new IllegalArgumentException("nome inválido: use de 1 a 30 caracteres");
        }
        if (nome.contains(">")) {
            throw new IllegalArgumentException("o novo nome não pode conter `>`");
        }

        long id = exigir(c, usuario, caminho, tipo);
        Long pai;
        try (PreparedStatement ps = c.prepareStatement("SELECT pai_id FROM categoria WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                pai = rs.getObject(1) == null ? null : rs.getLong(1);
            }
        }

        Long conflito = buscar(c, usuario, nome, pai, tipo);
        if (conflito != null && conflito != id) {
            throw new IllegalArgumentException("já existe `%s` nesse nível".formatted(nome));
        }

        try (PreparedStatement ps = c.prepareStatement("UPDATE categoria SET nome = ? WHERE id = ?")) {
            ps.setString(1, nome);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    /** Apaga a categoria e suas subcategorias. Recusa se houver lançamento, para não perder dado. */
    static void excluir(Connection c, String usuario, String caminho, String tipo)
            throws SQLException {
        long id = exigir(c, usuario, caminho, tipo);

        try (PreparedStatement ps = c.prepareStatement("""
                SELECT COUNT(*) FROM lancamento
                 WHERE usuario = ?
                   AND (categoria_id = ?
                        OR categoria_id IN (SELECT id FROM categoria WHERE pai_id = ?))""")) {
            ps.setString(1, usuario);
            ps.setLong(2, id);
            ps.setLong(3, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int usos = rs.getInt(1);
                if (usos > 0) {
                    throw new IllegalArgumentException(
                            "`%s` tem %d lançamento(s) — renomeie, ou apague os lançamentos antes"
                                    .formatted(caminho, usos));
                }
            }
        }

        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM categoria WHERE usuario = ? AND (id = ? OR pai_id = ?)")) {
            ps.setString(1, usuario);
            ps.setLong(2, id);
            ps.setLong(3, id);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------ lançamentos

    /** Valor negativo é despesa, positivo é ganho. Devolve o id gerado. */
    static long registrar(Connection c, String usuario, long centavos, long categoriaId,
                          String descricao, LocalDate data) throws SQLException {
        if (descricao != null && descricao.length() > 200) {
            throw new IllegalArgumentException("descrição muito longa: são até 200 caracteres");
        }
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO lancamento (usuario, valor_centavos, categoria_id, descricao, data)
                VALUES (?,?,?,?,?)""", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, usuario);
            ps.setLong(2, centavos);
            ps.setLong(3, categoriaId);
            ps.setString(4, descricao);
            ps.setString(5, data.toString());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    static Optional<Lancamento> buscar(Connection c, String usuario, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT l.id, l.data, l.valor_centavos,
                       COALESCE(p.nome || ' > ', '') || c.nome, l.descricao
                  FROM lancamento l
                  JOIN categoria c ON c.id = l.categoria_id
                  LEFT JOIN categoria p ON p.id = c.pai_id
                 WHERE l.id = ? AND l.usuario = ?""")) {
            ps.setLong(1, id);
            ps.setString(2, usuario);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new Lancamento(rs.getLong(1), LocalDate.parse(rs.getString(2)),
                                rs.getLong(3), rs.getString(4), rs.getString(5)))
                        : Optional.empty();
            }
        }
    }

    static boolean atualizar(Connection c, String usuario, long id, long centavos,
                             long categoriaId, String descricao, LocalDate data) throws SQLException {
        if (descricao != null && descricao.length() > 200) {
            throw new IllegalArgumentException("descrição muito longa: são até 200 caracteres");
        }
        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE lancamento
                   SET valor_centavos = ?, categoria_id = ?, descricao = ?, data = ?
                 WHERE id = ? AND usuario = ?""")) {
            ps.setLong(1, centavos);
            ps.setLong(2, categoriaId);
            ps.setString(3, descricao);
            ps.setString(4, data.toString());
            ps.setLong(5, id);
            ps.setString(6, usuario);
            return ps.executeUpdate() > 0;
        }
    }

    static boolean remover(Connection c, String usuario, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM lancamento WHERE id = ? AND usuario = ?")) {
            ps.setLong(1, id);
            ps.setString(2, usuario);
            return ps.executeUpdate() > 0;
        }
    }

    static List<Lancamento> lancamentos(Connection c, String usuario, Periodo p,
                                        int limite, int offset) throws SQLException {
        List<Lancamento> lista = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT l.id, l.data, l.valor_centavos,
                       COALESCE(p.nome || ' > ', '') || c.nome, l.descricao
                  FROM lancamento l
                  JOIN categoria c ON c.id = l.categoria_id
                  LEFT JOIN categoria p ON p.id = c.pai_id
                 WHERE l.usuario = ? AND l.data BETWEEN ? AND ?
                 ORDER BY l.data DESC, l.id DESC
                 LIMIT ? OFFSET ?""")) {
            ps.setString(1, usuario);
            ps.setString(2, p.inicio().toString());
            ps.setString(3, p.fim().toString());
            ps.setInt(4, limite);
            ps.setInt(5, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Lancamento(rs.getLong(1), LocalDate.parse(rs.getString(2)),
                            rs.getLong(3), rs.getString(4), rs.getString(5)));
                }
            }
        }
        return lista;
    }

    // ----------------------------------------------------------- recorrentes


    static long adicionarRecorrente(Connection c, String usuario, long centavos, long categoriaId,
                                    String descricao) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO recorrente (usuario, valor_centavos, categoria_id, descricao)
                VALUES (?,?,?,?)""", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, usuario);
            ps.setLong(2, centavos);
            ps.setLong(3, categoriaId);
            ps.setString(4, descricao);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    static List<Recorrente> recorrentes(Connection c, String usuario) throws SQLException {
        List<Recorrente> lista = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT r.id, r.valor_centavos,
                       COALESCE(p.nome || ' > ', '') || c.nome, r.descricao
                  FROM recorrente r
                  JOIN categoria c ON c.id = r.categoria_id
                  LEFT JOIN categoria p ON p.id = c.pai_id
                 WHERE r.usuario = ?
                 ORDER BY r.id""")) {
            ps.setString(1, usuario);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Recorrente(rs.getLong(1), rs.getLong(2),
                            rs.getString(3), rs.getString(4)));
                }
            }
        }
        return lista;
    }

    static boolean removerRecorrente(Connection c, String usuario, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM recorrente WHERE id = ? AND usuario = ?")) {
            ps.setLong(1, id);
            ps.setString(2, usuario);
            return ps.executeUpdate() > 0;
        }
    }

    /** Cria um lançamento para cada recorrente. Devolve quantos entraram. */
    static int lancarRecorrentes(Connection c, String usuario, LocalDate data) throws SQLException {
        int total = 0;
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO lancamento (usuario, valor_centavos, categoria_id, descricao, data)
                SELECT usuario, valor_centavos, categoria_id, descricao, ?
                  FROM recorrente WHERE usuario = ?""")) {
            ps.setString(1, data.toString());
            ps.setString(2, usuario);
            total = ps.executeUpdate();
        }
        return total;
    }

    // ---------------------------------------------------------------- resumo

    static Resumo resumo(Connection c, String usuario, Periodo p) throws SQLException {
        long ganhos = 0;
        long despesas = 0;

        try (PreparedStatement ps = c.prepareStatement("""
                SELECT COALESCE(SUM(CASE WHEN valor_centavos > 0 THEN valor_centavos END), 0),
                       COALESCE(SUM(CASE WHEN valor_centavos < 0 THEN -valor_centavos END), 0)
                  FROM lancamento
                 WHERE usuario = ? AND data BETWEEN ? AND ?""")) {
            ps.setString(1, usuario);
            ps.setString(2, p.inicio().toString());
            ps.setString(3, p.fim().toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ganhos = rs.getLong(1);
                    despesas = rs.getLong(2);
                }
            }
        }
        return new Resumo(ganhos, despesas,
                porCategoria(c, usuario, p, false), porCategoria(c, usuario, p, true));
    }

    /** Agrupa pela categoria raiz: subcategoria soma no pai. */
    private static List<Linha> porCategoria(Connection c, String usuario, Periodo p, boolean positivos)
            throws SQLException {
        String sinal = positivos ? "l.valor_centavos > 0" : "l.valor_centavos < 0";
        String total = positivos ? "SUM(l.valor_centavos)" : "SUM(-l.valor_centavos)";
        String sql = """
                SELECT COALESCE(p.nome, c.nome) AS grupo, %s AS total
                  FROM lancamento l
                  JOIN categoria c ON c.id = l.categoria_id
                  LEFT JOIN categoria p ON p.id = c.pai_id
                 WHERE l.usuario = ? AND l.data BETWEEN ? AND ? AND %s
                 GROUP BY grupo
                 ORDER BY total DESC""".formatted(total, sinal);

        List<Linha> linhas = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, usuario);
            ps.setString(2, p.inicio().toString());
            ps.setString(3, p.fim().toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    linhas.add(new Linha(rs.getString(1), rs.getLong(2)));
                }
            }
        }
        return linhas;
    }

    /** Quanto já foi gasto (ou ganho) na raiz desta categoria dentro do período. */
    static long totalDaCategoria(Connection c, String usuario, long categoriaId, Periodo p,
                                 boolean ganho) throws SQLException {
        String sql = """
                SELECT COALESCE(SUM(%s), 0)
                  FROM lancamento l JOIN categoria c ON c.id = l.categoria_id
                 WHERE l.usuario = ? AND l.data BETWEEN ? AND ? AND l.valor_centavos %s 0
                   AND COALESCE(c.pai_id, c.id) =
                       (SELECT COALESCE(pai_id, id) FROM categoria WHERE id = ?)"""
                .formatted(ganho ? "l.valor_centavos" : "-l.valor_centavos", ganho ? ">" : "<");
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, usuario);
            ps.setString(2, p.inicio().toString());
            ps.setString(3, p.fim().toString());
            ps.setLong(4, categoriaId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    /**
     * Valor típico da categoria nos últimos 90 dias, ignorando o lançamento recém-criado.
     * Devolve 0 quando não há histórico suficiente para a comparação significar algo.
     */
    static long valorTipico(Connection c, String usuario, long categoriaId, LocalDate hoje)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT AVG(ABS(l.valor_centavos)), COUNT(*)
                  FROM lancamento l JOIN categoria c ON c.id = l.categoria_id
                 WHERE l.usuario = ? AND l.data BETWEEN ? AND ?
                   AND COALESCE(c.pai_id, c.id) =
                       (SELECT COALESCE(pai_id, id) FROM categoria WHERE id = ?)""")) {
            ps.setString(1, usuario);
            ps.setString(2, hoje.minusDays(DIAS_DO_NORMAL).toString());
            ps.setString(3, hoje.toString());
            ps.setLong(4, categoriaId);
            try (ResultSet rs = ps.executeQuery()) {
                // Menos de 3 lançamentos não formam padrão — qualquer valor pareceria atípico.
                return rs.next() && rs.getInt(2) >= 3 ? rs.getLong(1) : 0;
            }
        }
    }

    // ------------------------------------------------------------- pendentes

    static void guardarPendente(Connection c, String chave, String usuario, Pedido p, Instant agora)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO pendente (chave, usuario, valor_centavos, categoria, descricao, data, criado_em)
                VALUES (?,?,?,?,?,?,?)""")) {
            ps.setString(1, chave);
            ps.setString(2, usuario);
            ps.setLong(3, p.centavos());
            ps.setString(4, p.categoria());
            ps.setString(5, p.descricao());
            ps.setString(6, p.data().toString());
            ps.setString(7, instante(agora));
            ps.executeUpdate();
        }
    }

    /** Apaga e devolve o pendente, se for da pessoa e tiver sido criado depois de {@code limite}. */
    static Optional<Pedido> retirarPendente(Connection c, String usuario, String chave, Instant limite)
            throws SQLException {
        Optional<Pedido> pedido = Optional.empty();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT valor_centavos, categoria, descricao, data, criado_em
                  FROM pendente WHERE chave = ? AND usuario = ?""")) {
            ps.setString(1, chave);
            ps.setString(2, usuario);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && Instant.parse(rs.getString(5)).isAfter(limite)) {
                    pedido = Optional.of(new Pedido(rs.getLong(1), rs.getString(2), rs.getString(3),
                            LocalDate.parse(rs.getString(4))));
                }
            }
        }
        // Só quem apagou grava: dois confirmar do mesmo pendente não viram dois lançamentos.
        return descartarPendente(c, usuario, chave) ? pedido : Optional.empty();
    }

    static boolean descartarPendente(Connection c, String usuario, String chave) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM pendente WHERE chave = ? AND usuario = ?")) {
            ps.setString(1, chave);
            ps.setString(2, usuario);
            return ps.executeUpdate() > 0;
        }
    }

    /** Truncado ao segundo, o ISO-8601 em UTC tem largura fixa: a ordem do texto é a do tempo. */
    private static String instante(Instant i) {
        return i.truncatedTo(ChronoUnit.SECONDS).toString();
    }

    static void expirarPendentes(Connection c, String usuario, Instant limite) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM pendente WHERE usuario = ? AND criado_em <= ?")) {
            ps.setString(1, usuario);
            ps.setString(2, instante(limite));
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------- sugestões

    /** Categoria existente parecida com o nome digitado, para pegar erro de digitação. */
    static Optional<String> parecida(Connection c, String usuario, String nome, String tipo)
            throws SQLException {
        String alvo = nome.toLowerCase(Locale.ROOT).trim();
        String melhor = null;
        int menor = Integer.MAX_VALUE;

        for (String caminho : caminhos(c, usuario, tipo, "", 200)) {
            String folha = caminho.contains(">")
                    ? caminho.substring(caminho.indexOf('>') + 1).trim()
                    : caminho;
            int d = distancia(alvo, folha.toLowerCase(Locale.ROOT));
            if (d > 0 && d < menor) {
                menor = d;
                melhor = caminho;
            }
        }
        // Tolerância cresce com o tamanho: erro de uma letra em "pet" não é o mesmo que em "alimentação".
        return menor <= Math.max(1, alvo.length() / 4) ? Optional.ofNullable(melhor) : Optional.empty();
    }

    private static int distancia(String a, String b) {
        int[] anterior = new int[b.length() + 1];
        int[] atual = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            anterior[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            atual[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int troca = anterior[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                atual[j] = Math.min(troca, Math.min(anterior[j] + 1, atual[j - 1] + 1));
            }
            int[] troca = anterior;
            anterior = atual;
            atual = troca;
        }
        return anterior[b.length()];
    }

}
