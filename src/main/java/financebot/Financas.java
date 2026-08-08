package financebot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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

    private static final List<String> PADRAO_GASTO = List.of(
            "Alimentação", "Transporte", "Moradia", "Saúde", "Lazer", "Educação", "Outros");
    private static final List<String> PADRAO_GANHO = List.of(
            "Salário", "Freelance", "Investimentos", "Outros");

    private Financas() {}

    // ---------------------------------------------------------------- modelo

    record Linha(String categoria, long total) {}

    record Lancamento(long id, LocalDate data, long centavos, String categoria, String descricao) {}

    record Resumo(long ganhos, long despesas, List<Linha> porDespesa, List<Linha> porGanho) {
        long saldo() {
            return ganhos - despesas;
        }

        boolean vazio() {
            return ganhos == 0 && despesas == 0;
        }
    }

    record Periodo(LocalDate inicio, LocalDate fim, ChronoUnit unidade) {

        static Periodo de(String tipo, LocalDate hoje) {
            return switch (tipo) {
                case "dia" -> new Periodo(hoje, hoje, ChronoUnit.DAYS);
                case "semana" -> new Periodo(
                        hoje.with(DayOfWeek.MONDAY), hoje.with(DayOfWeek.SUNDAY), ChronoUnit.WEEKS);
                case "ano" -> new Periodo(
                        hoje.withDayOfYear(1), hoje.withDayOfYear(hoje.lengthOfYear()), ChronoUnit.YEARS);
                default -> new Periodo(
                        hoje.withDayOfMonth(1), hoje.withDayOfMonth(hoje.lengthOfMonth()), ChronoUnit.MONTHS);
            };
        }

        Periodo anterior() {
            return switch (unidade) {
                case DAYS -> new Periodo(inicio.minusDays(1), fim.minusDays(1), unidade);
                case WEEKS -> new Periodo(inicio.minusWeeks(1), fim.minusWeeks(1), unidade);
                case YEARS -> new Periodo(inicio.minusYears(1), fim.minusYears(1), unidade);
                default -> {
                    LocalDate i = inicio.minusMonths(1);
                    yield new Periodo(i, i.withDayOfMonth(i.lengthOfMonth()), unidade);
                }
            };
        }
    }

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
        }
    }

    /**
     * Cria as categorias iniciais na primeira vez que o usuário aparece.
     * São dele, e não do sistema, justamente para que possa renomear e excluir.
     */
    /** @return true na primeira vez, para quem chama poder dar as boas-vindas. */
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
            throw new IllegalArgumentException("informe uma categoria");
        }
        String[] partes = caminho.split(">");
        if (partes.length > 2) {
            throw new IllegalArgumentException("são no máximo dois níveis: `Categoria > Subcategoria`");
        }
        for (int i = 0; i < partes.length; i++) {
            partes[i] = Entrada.capitalizar(partes[i]);
            if (partes[i].isEmpty()) {
                throw new IllegalArgumentException("categoria inválida: " + caminho);
            }
            if (partes[i].length() > 30) {
                throw new IllegalArgumentException("nome muito longo: " + partes[i]);
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
            throw new IllegalArgumentException("descrição muito longa");
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
            throw new IllegalArgumentException("descrição muito longa");
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

    record Recorrente(long id, long centavos, String categoria, String descricao) {}

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
                 WHERE l.usuario = ? AND l.data >= ?
                   AND COALESCE(c.pai_id, c.id) =
                       (SELECT COALESCE(pai_id, id) FROM categoria WHERE id = ?)""")) {
            ps.setString(1, usuario);
            ps.setString(2, hoje.minusDays(90).toString());
            ps.setLong(3, categoriaId);
            try (ResultSet rs = ps.executeQuery()) {
                // Menos de 3 lançamentos não formam padrão — qualquer valor pareceria atípico.
                return rs.next() && rs.getInt(2) >= 3 ? rs.getLong(1) : 0;
            }
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

    /** Variação percentual. Sem base de comparação devolve travessão, nunca divisão por zero. */
    static String variacao(long atual, long anterior) {
        if (anterior == 0) {
            return "—";
        }
        double pct = (atual - anterior) * 100.0 / Math.abs(anterior);
        return String.format(Locale.of("pt", "BR"), "%+.1f%%", pct);
    }

    // ----------------------------------------------------------------- datas

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("d/M/yyyy");

    /** Aceita "DD/MM", "AAAA-MM-DD" ou vazio (hoje). Rejeita data futura. */
    static LocalDate parseData(String texto, LocalDate hoje) {
        if (texto == null || texto.isBlank()) {
            return hoje;
        }
        String limpo = texto.trim();
        if (limpo.matches("\\d{1,2}")) {
            limpo = limpo + "/" + hoje.getMonthValue();   // só o dia: mês corrente
        }
        LocalDate data;
        try {
            data = limpo.contains("/")
                    ? LocalDate.parse(limpo + "/" + hoje.getYear(), DIA_MES)
                    : LocalDate.parse(limpo);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("data inválida: use DD/MM ou AAAA-MM-DD");
        }
        // "25/12" digitado em janeiro é dezembro do ano passado, não do que vem.
        if (data.isAfter(hoje) && limpo.contains("/")) {
            data = data.minusYears(1);
        }
        if (data.isAfter(hoje)) {
            throw new IllegalArgumentException("a data não pode ser futura");
        }
        return data;
    }
}
