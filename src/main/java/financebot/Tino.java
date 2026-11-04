package financebot;

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
final class Tino {

    static final String PADRAO = "Outros";
    static final int LIMITE_PAGINA = 20;

    // ponytail: uma conexão só, serializada pelos métodos synchronized. Um app pessoal
    // não tem concorrência que justifique pool; se tiver, trocar por HikariCP.
    private final Connection conexao;

    /** Um lançamento gravado, com o contexto que ajuda a pegar erro de digitação. */
    record Registro(long id, long centavos, String categoria, String descricao, LocalDate data,
                    long acumulado, long tipico, String parecida) {}

    record Pagina(Financas.Periodo periodo, List<Financas.Lancamento> itens, int offset,
                  boolean temProxima) {}

    /** {@code ritmo} é nulo quando não há projeção a fazer. */
    record Comparativo(Financas.Periodo periodo, Financas.Resumo atual, Financas.Resumo anterior,
                       Ritmo ritmo) {}

    record Ritmo(long mediaDiaria, long projecao) {}

    record Exportacao(Financas.Periodo periodo, int quantidade, String csv) {}

    record Categorias(List<String> gastos, int raizGasto, int subGasto,
                      List<String> ganhos, int raizGanho, int subGanho) {}

    Tino(Connection conexao) {
        this.conexao = conexao;
    }

    static Tino abrir(String caminho) throws SQLException {
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
    synchronized String conta(String provedor, String externo) throws SQLException {
        return Financas.conta(conexao, provedor, externo);
    }

    /** Liga outra identidade (ex.: Google) à conta de quem já está logado. */
    synchronized void vincular(String conta, String provedor, String externo) throws SQLException {
        Financas.vincular(conexao, conta, provedor, externo);
    }

    /** @return true na primeira vez, para o canal poder dar as boas-vindas. */
    synchronized boolean preparar(String usuario) throws SQLException {
        return Financas.prepararUsuario(conexao, usuario);
    }

    // ------------------------------------------------------------ lançamentos

    /** Valor negativo é gasto, positivo é ganho. Categoria nula vai para Outros. */
    synchronized Registro registrar(String usuario, long centavos, String categoria,
                                    String descricao, LocalDate data) throws SQLException {
        String nome = categoria == null || categoria.isBlank() ? PADRAO : categoria;
        boolean ganho = centavos > 0;
        String tipo = ganho ? Financas.GANHO : Financas.GASTO;

        // Antes de criar: se não existe mas há uma parecida, vale avisar do provável engano.
        String parecida = Financas.caminhos(conexao, usuario, tipo, nome, 1).isEmpty()
                ? Financas.parecida(conexao, usuario, nome, tipo).orElse(null)
                : null;

        long categoriaId = Financas.categoriaId(conexao, usuario, nome, tipo);
        long tipico = Financas.valorTipico(conexao, usuario, categoriaId, data);
        long id = Financas.registrar(conexao, usuario, centavos, categoriaId, descricao, data);
        long acumulado = Financas.totalDaCategoria(conexao, usuario, categoriaId,
                Financas.Periodo.de("mes", data), ganho);

        return new Registro(id, centavos, nome, descricao, data, acumulado, tipico, parecida);
    }

    /** Texto livre: "50 mercado, 30 uber, +3000 salário". */
    synchronized List<Registro> lancar(String usuario, String texto, LocalDate hoje)
            throws SQLException {
        // As categorias existentes permitem reconhecer nomes com espaço sem exigir aspas.
        List<Entrada.Item> itens = Entrada.lista(texto,
                Set.copyOf(Financas.caminhos(conexao, usuario, null, "", 200)));
        // ponytail: sem transação; um item inválido no meio deixa os anteriores gravados.
        List<Registro> registros = new ArrayList<>();
        for (Entrada.Item item : itens) {
            registros.add(registrar(usuario, item.ganho() ? item.centavos() : -item.centavos(),
                    item.categoria(), item.descricao(), Entrada.data(item.data(), hoje)));
        }
        return registros;
    }

    /** Mantém o sinal original: edição não transforma gasto em ganho. */
    synchronized Registro editar(String usuario, long id, long centavos, String categoria,
                                 String descricao, LocalDate data) throws SQLException {
        Financas.Lancamento antes = Financas.buscar(conexao, usuario, id).orElseThrow(
                () -> new IllegalArgumentException("esse lançamento já não existe"));
        boolean ganho = antes.centavos() > 0;
        long comSinal = ganho ? centavos : -centavos;

        long categoriaId = Financas.categoriaId(conexao, usuario, categoria,
                ganho ? Financas.GANHO : Financas.GASTO);
        Financas.atualizar(conexao, usuario, id, comSinal, categoriaId, descricao, data);
        return new Registro(id, comSinal, categoria, descricao, data, 0, 0, null);
    }

    synchronized Optional<Financas.Lancamento> buscar(String usuario, long id) throws SQLException {
        return Financas.buscar(conexao, usuario, id);
    }

    synchronized boolean remover(String usuario, long id) throws SQLException {
        return Financas.remover(conexao, usuario, id);
    }

    // -------------------------------------------------------------- consultas

    synchronized Comparativo resumo(String usuario, String tipoPeriodo, LocalDate hoje)
            throws SQLException {
        Financas.Periodo periodo = Financas.Periodo.de(tipoPeriodo, hoje);
        Financas.Resumo atual = Financas.resumo(conexao, usuario, periodo);
        return new Comparativo(periodo, atual,
                Financas.resumo(conexao, usuario, periodo.anterior()),
                ritmo(periodo, atual.despesas(), hoje));
    }

    /**
     * Média diária e projeção até o fim do período. Só faz sentido com o período
     * em andamento — período encerrado já tem o total real, não uma estimativa.
     */
    static Ritmo ritmo(Financas.Periodo p, long despesas, LocalDate hoje) {
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

    synchronized Pagina pagina(String usuario, String tipoPeriodo, int offset, LocalDate hoje)
            throws SQLException {
        Financas.Periodo periodo = Financas.Periodo.de(tipoPeriodo, hoje);
        // Busca um a mais do que cabe: se vier, existe página seguinte.
        List<Financas.Lancamento> lista =
                Financas.lancamentos(conexao, usuario, periodo, LIMITE_PAGINA + 1, offset);
        boolean temProxima = lista.size() > LIMITE_PAGINA;
        return new Pagina(periodo, temProxima ? lista.subList(0, LIMITE_PAGINA) : lista,
                offset, temProxima);
    }

    synchronized Exportacao exportar(String usuario, String tipoPeriodo, LocalDate hoje)
            throws SQLException {
        Financas.Periodo periodo = Financas.Periodo.de(tipoPeriodo, hoje);
        List<Financas.Lancamento> lista =
                Financas.lancamentos(conexao, usuario, periodo, Integer.MAX_VALUE, 0);
        return new Exportacao(periodo, lista.size(), csv(lista));
    }

    /** Uma linha por lançamento, com cabeçalho. Ponto decimal para abrir em planilha. */
    static String csv(List<Financas.Lancamento> lista) {
        StringBuilder sb = new StringBuilder("id,data,valor,categoria,descricao\n");
        for (Financas.Lancamento l : lista) {
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
    synchronized List<String> caminhos(String usuario, String tipo, String filtro, int limite)
            throws SQLException {
        return Financas.caminhos(conexao, usuario, tipo, filtro, limite);
    }

    synchronized Categorias categorias(String usuario) throws SQLException {
        return new Categorias(
                Financas.caminhos(conexao, usuario, Financas.GASTO, "", 200),
                Financas.contar(conexao, usuario, Financas.GASTO, true),
                Financas.contar(conexao, usuario, Financas.GASTO, false),
                Financas.caminhos(conexao, usuario, Financas.GANHO, "", 200),
                Financas.contar(conexao, usuario, Financas.GANHO, true),
                Financas.contar(conexao, usuario, Financas.GANHO, false));
    }

    synchronized void criarCategoria(String usuario, String caminho, String tipo) throws SQLException {
        Financas.categoriaId(conexao, usuario, caminho, tipo);
    }

    synchronized void renomearCategoria(String usuario, String caminho, String novo, String tipo)
            throws SQLException {
        Financas.renomear(conexao, usuario, caminho, novo, tipo);
    }

    synchronized void excluirCategoria(String usuario, String caminho, String tipo)
            throws SQLException {
        Financas.excluir(conexao, usuario, caminho, tipo);
    }

    // ------------------------------------------------------------------ fixos

    synchronized List<Financas.Recorrente> fixos(String usuario) throws SQLException {
        return Financas.recorrentes(conexao, usuario);
    }

    /** Valor negativo é gasto, positivo é ganho. */
    synchronized void adicionarFixo(String usuario, long centavos, String categoria,
                                    String descricao) throws SQLException {
        long categoriaId = Financas.categoriaId(conexao, usuario, categoria,
                centavos > 0 ? Financas.GANHO : Financas.GASTO);
        Financas.adicionarRecorrente(conexao, usuario, centavos, categoriaId, descricao);
    }

    synchronized boolean removerFixo(String usuario, long id) throws SQLException {
        return Financas.removerRecorrente(conexao, usuario, id);
    }

    synchronized int lancarFixos(String usuario, LocalDate data) throws SQLException {
        return Financas.lancarRecorrentes(conexao, usuario, data);
    }
}
