package tino.nucleo;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * O que o Tino sabe fazer, sem saber por qual canal foi pedido. Discord, site e
 * MCP chamam isto; nada aqui conhece JDA, HTTP ou embed.
 *
 * <p>Recebe valores já interpretados — centavos com sinal, datas, nomes de
 * categoria. Interpretar texto digitado é papel de {@link Entrada}, chamada por
 * quem recebe texto livre.
 *
 * <p>O "agora" vem do {@link Clock}, no fuso do Tino: data futura, madrugada, mês corrente
 * e expiração de pendente usam o mesmo relógio, e nenhum canal calcula a data.
 */
public final class Tino {

    public static final String PADRAO = "Outros";
    public static final int LIMITE_PAGINA = 20;
    public static final int MAX_RAIZ = Financas.MAX_RAIZ;
    public static final int MAX_SUB = Financas.MAX_SUB;

    // ponytail: um fuso para todo mundo. Fuso por pessoa entra com o site, numa coluna em conta.
    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    static final Duration VALIDADE_PENDENTE = Duration.ofHours(24);

    // ponytail: uma conexão só, serializada pelos métodos synchronized. Um app pessoal
    // não tem concorrência que justifique pool; se tiver, trocar por HikariCP.
    private final Connection conexao;
    private final Clock relogio;

    /**
     * Um lançamento gravado, com o contexto que ajuda a pegar erro de digitação.
     * {@code madrugada}: gravado entre 0h e 6h no fuso do Tino.
     */
    public record Registro(long id, long centavos, String categoria, String descricao, LocalDate data,
                           long acumulado, String parecida, boolean madrugada) {}

    public record Pagina(Periodo periodo, List<Lancamento> itens, int offset,
                  boolean temProxima) {}

    /** {@code ritmo} é nulo quando não há projeção a fazer. */
    public record Comparativo(Periodo periodo, Resumo atual, Resumo anterior,
                       Ritmo ritmo) {}

    public record Ritmo(long mediaDiaria, long projecao) {}

    public record Exportacao(Periodo periodo, int quantidade, String csv) {}

    public record Categorias(List<String> gastos, int raizGasto, int subGasto,
                      List<String> ganhos, int raizGanho, int subGanho) {}

    Tino(Connection conexao, Clock relogio) {
        this.conexao = conexao;
        this.relogio = relogio;
    }

    public static Tino abrir(String caminho) throws SQLException {
        return abrir(caminho, Clock.system(FUSO));
    }

    static Tino abrir(String caminho, Clock relogio) throws SQLException {
        Connection conexao = DriverManager.getConnection("jdbc:sqlite:" + caminho);
        try (Statement s = conexao.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA busy_timeout=5000");
        }
        Financas.criarTabelas(conexao);
        Financas.normalizarCategorias(conexao);
        Financas.migrarParaContas(conexao);
        return new Tino(conexao, relogio);
    }

    ZoneId fuso() {
        return relogio.getZone();
    }

    /** Para o canal interpretar uma data digitada ("07/08") com o mesmo dia do núcleo. */
    public LocalDate hoje() {
        return LocalDate.now(relogio);
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
     * Grava o pedido, a não ser que o valor fuja muito do normal da pessoa: aí guarda o
     * pedido como pendente e devolve {@link Evento.ForaDoNormal} com a chave, para o canal
     * perguntar antes.
     */
    public synchronized Evento lancar(String usuario, Pedido p) throws SQLException {
        exigirPassado(p);
        Financas.expirarPendentes(conexao, usuario, relogio.instant().minus(VALIDADE_PENDENTE));
        Pedido pedido = new Pedido(p.centavos(), nome(p), p.descricao(), p.data());
        // Categoria que ainda não existe não tem histórico: grava direto, e o registrar
        // ainda pode avisar de uma parecida antes de criá-la.
        if (!existe(usuario, pedido)) {
            return new Evento.Registrado(registrar(usuario, pedido));
        }
        long tipico = Financas.valorTipico(conexao, usuario, categoriaId(usuario, pedido), pedido.data());
        if (!foraDoNormal(pedido.centavos(), tipico)) {
            return new Evento.Registrado(registrar(usuario, pedido));
        }
        String chave = UUID.randomUUID().toString().replace("-", "");
        Financas.guardarPendente(conexao, chave, usuario, pedido, relogio.instant());
        return new Evento.ForaDoNormal(chave, pedido, tipico);
    }

    /** Grava o pendente que a pessoa confirmou. Vazio se já foi respondido, expirou ou é de outra. */
    public synchronized Optional<Registro> confirmar(String usuario, String chave) throws SQLException {
        return emTransacao(() -> {
            Optional<Pedido> p = Financas.retirarPendente(conexao, usuario, chave,
                    relogio.instant().minus(VALIDADE_PENDENTE));
            return p.isEmpty() ? Optional.<Registro>empty() : Optional.of(registrar(usuario, p.get()));
        });
    }

    /** A pessoa respondeu que o valor estava errado. False se já não havia o que descartar. */
    public synchronized boolean descartar(String usuario, String chave) throws SQLException {
        return Financas.descartarPendente(conexao, usuario, chave);
    }

    private void exigirPassado(Pedido p) {
        if (p.data().isAfter(hoje())) {
            throw new IllegalArgumentException("a data não pode ser futura");
        }
    }

    private interface Trabalho<T> {
        T fazer() throws SQLException;
    }

    /** Tudo ou nada: falha no meio desfaz o que já foi gravado. */
    private <T> T emTransacao(Trabalho<T> trabalho) throws SQLException {
        conexao.setAutoCommit(false);
        try {
            T resultado = trabalho.fazer();
            conexao.commit();
            return resultado;
        } catch (SQLException | RuntimeException e) {
            conexao.rollback();
            throw e;
        } finally {
            conexao.setAutoCommit(true);
        }
    }

    /**
     * Texto livre: "50 mercado, 30 uber, +3000 salário". Um evento por item, e tudo ou
     * nada: um item inválido desfaz os anteriores, e o erro diz qual foi.
     */
    public synchronized List<Evento> lancar(String usuario, String texto) throws SQLException {
        // As categorias existentes permitem reconhecer nomes com espaço sem exigir aspas.
        List<Entrada.Item> itens = Entrada.lista(texto,
                Set.copyOf(Financas.caminhos(conexao, usuario, null, "", 200)));
        return emTransacao(() -> {
            List<Evento> eventos = new ArrayList<>();
            for (int i = 0; i < itens.size(); i++) {
                Entrada.Item item = itens.get(i);
                try {
                    eventos.add(lancar(usuario, new Pedido(
                            item.ganho() ? item.centavos() : -item.centavos(),
                            item.categoria(), item.descricao(), Entrada.data(item.data(), hoje()))));
                } catch (IllegalArgumentException e) {
                    throw itens.size() == 1 ? e
                            : new IllegalArgumentException("No %dº item: %s".formatted(i + 1, e.getMessage()), e);
                }
            }
            return eventos;
        });
    }

    /** Grava sem perguntar. Fora do núcleo, só por {@link #lancar} ou {@link #confirmar}. */
    Registro registrar(String usuario, Pedido p) throws SQLException {
        String nome = nome(p);
        String tipo = tipo(p);

        // Antes de criar: se não existe mas há uma parecida, vale avisar do provável engano.
        String parecida = existe(usuario, p)
                ? null
                : Financas.parecida(conexao, usuario, nome, tipo).orElse(null);

        long categoriaId = categoriaId(usuario, p);
        long id = Financas.registrar(conexao, usuario, p.centavos(), categoriaId, p.descricao(), p.data());
        long acumulado = Financas.totalDaCategoria(conexao, usuario, categoriaId,
                Periodo.de(Escala.MES, p.data()), p.centavos() > 0);

        return new Registro(id, p.centavos(), nome, p.descricao(), p.data(), acumulado, parecida,
                madrugada());
    }

    Registro registrar(String usuario, long centavos, String categoria, String descricao, LocalDate data)
            throws SQLException {
        return registrar(usuario, new Pedido(centavos, categoria, descricao, data));
    }

    private boolean madrugada() {
        return LocalTime.now(relogio).getHour() < 6;
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

    private boolean existe(String usuario, Pedido p) throws SQLException {
        return !Financas.caminhos(conexao, usuario, tipo(p), nome(p), 1).isEmpty();
    }

    /** Cria a categoria se ainda não existe. */
    private long categoriaId(String usuario, Pedido p) throws SQLException {
        return Financas.categoriaId(conexao, usuario, nome(p), tipo(p));
    }

    /** Edição não transforma gasto em ganho: o pedido precisa ter o sinal do original. */
    public synchronized Registro editar(String usuario, long id, Pedido p) throws SQLException {
        Lancamento antes = Financas.buscar(conexao, usuario, id).orElseThrow(
                () -> new IllegalArgumentException("esse lançamento já não existe"));
        if ((antes.centavos() > 0) != (p.centavos() > 0)) {
            throw new IllegalArgumentException("a edição não troca gasto por ganho");
        }
        exigirPassado(p);
        return emTransacao(() -> {
            // A categoria nova só fica se o lançamento ainda existir.
            if (!Financas.atualizar(conexao, usuario, id, p.centavos(), categoriaId(usuario, p),
                    p.descricao(), p.data())) {
                throw new IllegalArgumentException("esse lançamento já não existe");
            }
            return new Registro(id, p.centavos(), nome(p), p.descricao(), p.data(), 0, null, false);
        });
    }

    public synchronized Optional<Lancamento> buscar(String usuario, long id) throws SQLException {
        return Financas.buscar(conexao, usuario, id);
    }

    public synchronized boolean remover(String usuario, long id) throws SQLException {
        return Financas.remover(conexao, usuario, id);
    }

    // -------------------------------------------------------------- consultas

    public synchronized Comparativo resumo(String usuario, Escala escala) throws SQLException {
        LocalDate hoje = hoje();
        Periodo periodo = Periodo.de(escala, hoje);
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

    public synchronized Pagina pagina(String usuario, Escala escala, int offset) throws SQLException {
        Periodo periodo = Periodo.de(escala, hoje());
        // Busca um a mais do que cabe: se vier, existe página seguinte.
        List<Lancamento> lista =
                Financas.lancamentos(conexao, usuario, periodo, LIMITE_PAGINA + 1, offset);
        boolean temProxima = lista.size() > LIMITE_PAGINA;
        return new Pagina(periodo, temProxima ? lista.subList(0, LIMITE_PAGINA) : lista,
                offset, temProxima);
    }

    public synchronized Exportacao exportar(String usuario, Escala escala) throws SQLException {
        Periodo periodo = Periodo.de(escala, hoje());
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

    /** Valor negativo é gasto, positivo é ganho. Valida como um lançamento de hoje. */
    public synchronized void adicionarFixo(String usuario, long centavos, String categoria,
                                    String descricao) throws SQLException {
        Pedido p = new Pedido(centavos, categoria, descricao, hoje());
        Financas.adicionarRecorrente(conexao, usuario, p.centavos(), categoriaId(usuario, p), p.descricao());
    }

    public synchronized boolean removerFixo(String usuario, long id) throws SQLException {
        return Financas.removerRecorrente(conexao, usuario, id);
    }

    public synchronized int lancarFixos(String usuario) throws SQLException {
        return Financas.lancarRecorrentes(conexao, usuario, hoje());
    }
}
