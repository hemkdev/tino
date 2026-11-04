package tino.nucleo;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * O que o Tino sabe fazer, sem saber por qual canal foi pedido. Discord, site e
 * MCP chamam isto; nada aqui conhece JDA, HTTP ou embed.
 *
 * <p>Recebe valores já interpretados — centavos com sinal, datas, nomes de
 * categoria. Interpretar texto digitado é papel de {@link Entrada}, chamada por
 * quem recebe texto livre.
 */
public final class Tino {

    public static final String PADRAO = "Outros";
    public static final int LIMITE_PAGINA = 20;
    public static final int MAX_RAIZ = Financas.MAX_RAIZ;
    public static final int MAX_SUB = Financas.MAX_SUB;

    // ponytail: uma conexão só, serializada pelos métodos synchronized. Um app pessoal
    // não tem concorrência que justifique pool; se tiver, trocar por HikariCP.
    private final Connection conexao;

    /** Um lançamento gravado, com o contexto que ajuda a pegar erro de digitação. */
    public record Registro(long id, long centavos, String categoria, String descricao, LocalDate data,
                           long acumulado, String parecida) {}

    public record Pagina(Periodo periodo, List<Lancamento> itens, int offset,
                  boolean temProxima) {}

    /** {@code ritmo} é nulo quando não há projeção a fazer. */
    public record Comparativo(Periodo periodo, Resumo atual, Resumo anterior,
                       Ritmo ritmo) {}

    public record Ritmo(long mediaDiaria, long projecao) {}

    public record Exportacao(Periodo periodo, int quantidade, String csv) {}

    public record Categorias(List<String> gastos, int raizGasto, int subGasto,
                      List<String> ganhos, int raizGanho, int subGanho) {}

    Tino(Connection conexao) {
        this.conexao = conexao;
    }

    public static Tino abrir(String caminho) throws SQLException {
        Connection conexao = DriverManager.getConnection("jdbc:sqlite:" + caminho);
        try (Statement s = conexao.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA busy_timeout=5000");
        }
        Financas.criarTabelas(conexao);
        Financas.normalizarCategorias(conexao);
        Financas.migrarParaContas(conexao);
        return new Tino(conexao);
    }

    /**
     * Conta de quem chegou por este canal, criada no primeiro contato. É o valor de
     * {@code usuario} em todos os outros métodos.
     *
     * @param provedor "discord", "google", "telegram"...
     * @param externo  id da pessoa naquele provedor
     */
    public synchronized String conta(String provedor, String externo) throws SQLException {
        return Financas.conta(conexao, provedor, externo);
    }

    /** Liga outra identidade (ex.: Google) à conta de quem já está logado. */
    public synchronized void vincular(String conta, String provedor, String externo) throws SQLException {
        Financas.vincular(conexao, conta, provedor, externo);
    }

    /** @return true na primeira vez, para o canal poder dar as boas-vindas. */
    public synchronized boolean preparar(String usuario) throws SQLException {
        return Financas.prepararUsuario(conexao, usuario);
    }

    // ------------------------------------------------------------ lançamentos

    /**
     * Grava o pedido, a não ser que o valor fuja muito do normal da pessoa: aí devolve
     * {@link Evento.ForaDoNormal} sem gravar nada, para o canal confirmar antes.
     */
    public synchronized Evento lancar(String usuario, Pedido p) throws SQLException {
        long tipico = Financas.valorTipico(conexao, usuario, categoriaId(usuario, p), p.data());
        return foraDoNormal(p.centavos(), tipico)
                ? new Evento.ForaDoNormal(p, tipico)
                : new Evento.Registrado(registrar(usuario, p));
    }

    /** Texto livre: "50 mercado, 30 uber, +3000 salário". Um evento por item. */
    public synchronized List<Evento> lancar(String usuario, String texto, LocalDate hoje)
            throws SQLException {
        // As categorias existentes permitem reconhecer nomes com espaço sem exigir aspas.
        List<Entrada.Item> itens = Entrada.lista(texto,
                Set.copyOf(Financas.caminhos(conexao, usuario, null, "", 200)));
        // ponytail: sem transação; um item inválido no meio deixa os anteriores gravados.
        List<Evento> eventos = new ArrayList<>();
        for (Entrada.Item item : itens) {
            eventos.add(lancar(usuario, new Pedido(item.ganho() ? item.centavos() : -item.centavos(),
                    item.categoria(), item.descricao(), Entrada.data(item.data(), hoje))));
        }
        return eventos;
    }

    /** Grava sem perguntar: depois de a pessoa confirmar, ou quando o canal já decidiu. */
    public synchronized Registro registrar(String usuario, Pedido p) throws SQLException {
        String nome = nome(p);
        String tipo = tipo(p);

        // Antes de criar: se não existe mas há uma parecida, vale avisar do provável engano.
        String parecida = Financas.caminhos(conexao, usuario, tipo, nome, 1).isEmpty()
                ? Financas.parecida(conexao, usuario, nome, tipo).orElse(null)
                : null;

        long categoriaId = categoriaId(usuario, p);
        long id = Financas.registrar(conexao, usuario, p.centavos(), categoriaId, p.descricao(), p.data());
        long acumulado = Financas.totalDaCategoria(conexao, usuario, categoriaId,
                Periodo.de("mes", p.data()), p.centavos() > 0);

        return new Registro(id, p.centavos(), nome, p.descricao(), p.data(), acumulado, parecida);
    }

    public synchronized Registro registrar(String usuario, long centavos, String categoria,
                                           String descricao, LocalDate data) throws SQLException {
        return registrar(usuario, new Pedido(centavos, categoria, descricao, data));
    }

    /**
     * Mais de 5x a média recente costuma ser dedo gordo. Sem histórico ({@code tipico}
     * zero) nada é fora do normal: qualquer valor pareceria atípico.
     */
    static boolean foraDoNormal(long centavos, long tipico) {
        return tipico > 0 && Math.abs(centavos) > tipico * 5;
    }

    private static String nome(Pedido p) {
        return p.categoria() == null || p.categoria().isBlank() ? PADRAO : p.categoria();
    }

    private static String tipo(Pedido p) {
        return p.centavos() > 0 ? Financas.GANHO : Financas.GASTO;
    }

    /**
     * Cria a categoria se ainda não existe. Categoria nova não tem histórico, então
     * nunca é fora do normal: chamar isto antes de decidir não deixa categoria órfã.
     */
    private long categoriaId(String usuario, Pedido p) throws SQLException {
        return Financas.categoriaId(conexao, usuario, nome(p), tipo(p));
    }

    /** Mantém o sinal original: edição não transforma gasto em ganho. */
    public synchronized Registro editar(String usuario, long id, long centavos, String categoria,
                                 String descricao, LocalDate data) throws SQLException {
        Lancamento antes = Financas.buscar(conexao, usuario, id).orElseThrow(
                () -> new IllegalArgumentException("esse lançamento já não existe"));
        boolean ganho = antes.centavos() > 0;
        long comSinal = ganho ? centavos : -centavos;

        long categoriaId = Financas.categoriaId(conexao, usuario, categoria,
                ganho ? Financas.GANHO : Financas.GASTO);
        Financas.atualizar(conexao, usuario, id, comSinal, categoriaId, descricao, data);
        return new Registro(id, comSinal, categoria, descricao, data, 0, null);
    }

    public synchronized Optional<Lancamento> buscar(String usuario, long id) throws SQLException {
        return Financas.buscar(conexao, usuario, id);
    }

    public synchronized boolean remover(String usuario, long id) throws SQLException {
        return Financas.remover(conexao, usuario, id);
    }

    // -------------------------------------------------------------- consultas

    public synchronized Comparativo resumo(String usuario, String tipoPeriodo, LocalDate hoje)
            throws SQLException {
        Periodo periodo = Periodo.de(tipoPeriodo, hoje);
        Resumo atual = Financas.resumo(conexao, usuario, periodo);
        return new Comparativo(periodo, atual,
                Financas.resumo(conexao, usuario, periodo.anterior()),
                ritmo(periodo, atual.despesas(), hoje));
    }

    /**
     * Média diária e projeção até o fim do período. Só faz sentido com o período
     * em andamento — período encerrado já tem o total real, não uma estimativa.
     */
    public static Ritmo ritmo(Periodo p, long despesas, LocalDate hoje) {
        if (despesas <= 0 || hoje.isBefore(p.inicio()) || hoje.isAfter(p.fim())) {
            return null;
        }
        long decorridos = ChronoUnit.DAYS.between(p.inicio(), hoje) + 1;
        long totais = ChronoUnit.DAYS.between(p.inicio(), p.fim()) + 1;
        if (decorridos >= totais) {
            return null;   // último dia: a projeção seria o próprio total
        }
        long media = despesas / decorridos;
        return new Ritmo(media, media * totais);
    }

    public synchronized Pagina pagina(String usuario, String tipoPeriodo, int offset, LocalDate hoje)
            throws SQLException {
        Periodo periodo = Periodo.de(tipoPeriodo, hoje);
        // Busca um a mais do que cabe: se vier, existe página seguinte.
        List<Lancamento> lista =
                Financas.lancamentos(conexao, usuario, periodo, LIMITE_PAGINA + 1, offset);
        boolean temProxima = lista.size() > LIMITE_PAGINA;
        return new Pagina(periodo, temProxima ? lista.subList(0, LIMITE_PAGINA) : lista,
                offset, temProxima);
    }

    public synchronized Exportacao exportar(String usuario, String tipoPeriodo, LocalDate hoje)
            throws SQLException {
        Periodo periodo = Periodo.de(tipoPeriodo, hoje);
        List<Lancamento> lista =
                Financas.lancamentos(conexao, usuario, periodo, Integer.MAX_VALUE, 0);
        return new Exportacao(periodo, lista.size(), csv(lista));
    }

    /** Uma linha por lançamento, com cabeçalho. Ponto decimal para abrir em planilha. */
    public static String csv(List<Lancamento> lista) {
        StringBuilder sb = new StringBuilder("id,data,valor,categoria,descricao\n");
        for (Lancamento l : lista) {
            sb.append("%d,%s,%s,%s,%s%n".formatted(
                    l.id(),
                    l.data(),
                    BigDecimal.valueOf(l.centavos(), 2).toPlainString(),
                    aspas(l.categoria()),
                    aspas(l.descricao())));
        }
        return sb.toString();
    }

    private static String aspas(String valor) {
        return valor == null ? "" : '"' + valor.replace("\"", "\"\"") + '"';
    }

    // ------------------------------------------------------------- categorias

    /** Tipo nulo traz gasto e ganho juntos. */
    public synchronized List<String> caminhos(String usuario, Tipo tipo, String filtro, int limite)
            throws SQLException {
        return Financas.caminhos(conexao, usuario, tipo == null ? null : tipo.codigo(), filtro, limite);
    }

    public synchronized Categorias categorias(String usuario) throws SQLException {
        return new Categorias(
                Financas.caminhos(conexao, usuario, Financas.GASTO, "", 200),
                Financas.contar(conexao, usuario, Financas.GASTO, true),
                Financas.contar(conexao, usuario, Financas.GASTO, false),
                Financas.caminhos(conexao, usuario, Financas.GANHO, "", 200),
                Financas.contar(conexao, usuario, Financas.GANHO, true),
                Financas.contar(conexao, usuario, Financas.GANHO, false));
    }

    public synchronized void criarCategoria(String usuario, String caminho, Tipo tipo) throws SQLException {
        Financas.categoriaId(conexao, usuario, caminho, tipo.codigo());
    }

    public synchronized void renomearCategoria(String usuario, String caminho, String novo, Tipo tipo)
            throws SQLException {
        Financas.renomear(conexao, usuario, caminho, novo, tipo.codigo());
    }

    public synchronized void excluirCategoria(String usuario, String caminho, Tipo tipo)
            throws SQLException {
        Financas.excluir(conexao, usuario, caminho, tipo.codigo());
    }

    // ------------------------------------------------------------------ fixos

    public synchronized List<Recorrente> fixos(String usuario) throws SQLException {
        return Financas.recorrentes(conexao, usuario);
    }

    /** Valor negativo é gasto, positivo é ganho. */
    public synchronized void adicionarFixo(String usuario, long centavos, String categoria,
                                    String descricao) throws SQLException {
        long categoriaId = Financas.categoriaId(conexao, usuario, categoria,
                centavos > 0 ? Financas.GANHO : Financas.GASTO);
        Financas.adicionarRecorrente(conexao, usuario, centavos, categoriaId, descricao);
    }

    public synchronized boolean removerFixo(String usuario, long id) throws SQLException {
        return Financas.removerRecorrente(conexao, usuario, id);
    }

    public synchronized int lancarFixos(String usuario, LocalDate data) throws SQLException {
        return Financas.lancarRecorrentes(conexao, usuario, data);
    }
}
