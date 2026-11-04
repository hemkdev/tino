package tino.nucleo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static tino.nucleo.Financas.GANHO;
import static tino.nucleo.Financas.GASTO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Banco em memória: abre em milissegundos, some ao fechar. */
class FinancasTest {

    private static final String EU = "111";
    private static final String OUTRO = "222";
    private static final LocalDate HOJE = LocalDate.of(2026, 8, 7);   // sexta-feira

    private Connection c;

    @BeforeEach
    void abrir() throws SQLException {
        c = DriverManager.getConnection("jdbc:sqlite::memory:");
        Financas.criarTabelas(c);
        Financas.prepararUsuario(c, EU);
        Financas.prepararUsuario(c, OUTRO);
    }

    @AfterEach
    void fechar() throws SQLException {
        c.close();
    }

    private void gasto(String usuario, String valor, String categoria, LocalDate data)
            throws SQLException {
        Financas.registrar(c, usuario, -Dinheiro.paraCentavos(valor),
                Financas.categoriaId(c, usuario, categoria, GASTO), null, data);
    }

    private void ganho(String usuario, String valor, String categoria) throws SQLException {
        Financas.registrar(c, usuario, Dinheiro.paraCentavos(valor),
                Financas.categoriaId(c, usuario, categoria, GANHO), null, HOJE);
    }

    private Resumo resumoDoMes(String usuario) throws SQLException {
        return Financas.resumo(c, usuario, Periodo.de("mes", HOJE));
    }

    // ---------------------------------------------------------------- resumo

    @Test
    void somaGanhosEDespesasDoPeriodo() throws SQLException {
        gasto(EU, "50", "Alimentação", HOJE);
        gasto(EU, "30", "Transporte", HOJE);
        ganho(EU, "3000", "Salário");

        Resumo r = resumoDoMes(EU);

        assertEquals(300_000, r.ganhos());
        assertEquals(8_000, r.despesas());
        assertEquals(292_000, r.saldo());
    }

    @Test
    void separaGanhosEDespesasEmListasProprias() throws SQLException {
        gasto(EU, "50", "Alimentação", HOJE);
        ganho(EU, "3000", "Salário");
        ganho(EU, "500", "Freelance");

        Resumo r = resumoDoMes(EU);

        assertEquals(List.of("Alimentação"), r.porDespesa().stream().map(Linha::categoria).toList());
        assertEquals(List.of("Salário", "Freelance"),
                r.porGanho().stream().map(Linha::categoria).toList());
        assertEquals(300_000, r.porGanho().get(0).total());
    }

    @Test
    void ignoraLancamentoDeOutroPeriodo() throws SQLException {
        gasto(EU, "50", "Alimentação", HOJE);
        gasto(EU, "999", "Alimentação", HOJE.minusMonths(1));

        assertEquals(5_000, resumoDoMes(EU).despesas());
    }

    @Test
    void naoVazaDadoEntreUsuarios() throws SQLException {
        gasto(EU, "50", "Alimentação", HOJE);
        gasto(OUTRO, "999", "Alimentação", HOJE);

        assertEquals(5_000, resumoDoMes(EU).despesas());
        assertEquals(99_900, resumoDoMes(OUTRO).despesas());
    }

    @Test
    void periodoSemLancamentoVemVazio() throws SQLException {
        assertTrue(resumoDoMes(EU).vazio());
    }

    // ----------------------------------------------------------- categorias

    @Test
    void subcategoriaSomaNoPai() throws SQLException {
        gasto(EU, "100", "Alimentação > Mercado", HOJE);
        gasto(EU, "40", "Alimentação > Restaurante", HOJE);
        gasto(EU, "30", "Transporte", HOJE);

        List<Linha> linhas = resumoDoMes(EU).porDespesa();

        assertEquals(2, linhas.size());
        assertEquals("Alimentação", linhas.get(0).categoria());
        assertEquals(14_000, linhas.get(0).total());
    }

    @Test
    void mesmoNomeEmTiposDiferentesSaoCategoriasDistintas() throws SQLException {
        long comoGasto = Financas.categoriaId(c, EU, "Outros", GASTO);
        long comoGanho = Financas.categoriaId(c, EU, "Outros", GANHO);

        assertTrue(comoGasto != comoGanho);
    }

    @Test
    void autocompleteNaoMisturaGastoComGanho() throws SQLException {
        assertTrue(Financas.caminhos(c, EU, GASTO, "", 25).contains("Alimentação"));
        assertFalse(Financas.caminhos(c, EU, GANHO, "", 25).contains("Alimentação"));
        assertTrue(Financas.caminhos(c, EU, GANHO, "", 25).contains("Salário"));
    }

    @Test
    void reusaCategoriaExistenteIgnorandoCaixa() throws SQLException {
        assertEquals(Financas.categoriaId(c, EU, "Lazer", GASTO),
                Financas.categoriaId(c, EU, "lazer", GASTO));
    }

    @Test
    void reusaCategoriaAcentuadaIgnorandoCaixa() throws SQLException {
        assertEquals(Financas.categoriaId(c, EU, "Alimentação", GASTO),
                Financas.categoriaId(c, EU, "ALIMENTAÇÃO", GASTO));
        assertEquals(Financas.categoriaId(c, EU, "Alimentação", GASTO),
                Financas.categoriaId(c, EU, "alimentação", GASTO));
    }

    @Test
    void gravaCategoriaNaFormaCanonica() throws SQLException {
        Financas.categoriaId(c, EU, "bem estar", GASTO);
        Financas.categoriaId(c, EU, "cartão DE crédito", GASTO);

        List<String> nomes = Financas.caminhos(c, EU, GASTO, "", 200);
        assertTrue(nomes.contains("Bem Estar"));
        assertTrue(nomes.contains("Cartão de Crédito"));
    }

    @Test
    void normalizaCategoriasGravadasAntes() throws SQLException {
        try (java.sql.Statement s = c.createStatement()) {
            s.execute("INSERT INTO categoria (usuario, pai_id, tipo, nome) "
                    + "VALUES ('" + EU + "', NULL, 'gasto', 'mercado antigo')");
        }

        Financas.normalizarCategorias(c);

        assertTrue(Financas.caminhos(c, EU, GASTO, "", 200).contains("Mercado Antigo"));
    }

    @Test
    void rejeitaMaisDeDoisNiveis() {
        assertThrows(IllegalArgumentException.class,
                () -> Financas.categoriaId(c, EU, "A > B > C", GASTO));
    }

    @Test
    void rejeitaCategoriaVazia() {
        assertThrows(IllegalArgumentException.class, () -> Financas.categoriaId(c, EU, "  ", GASTO));
        assertThrows(IllegalArgumentException.class, () -> Financas.categoriaId(c, EU, "Pai > ", GASTO));
    }

    @Test
    void categoriaDeUmUsuarioNaoApareceParaOutro() throws SQLException {
        Financas.categoriaId(c, EU, "Terapia", GASTO);

        assertTrue(Financas.caminhos(c, EU, GASTO, "", 25).contains("Terapia"));
        assertFalse(Financas.caminhos(c, OUTRO, GASTO, "", 25).contains("Terapia"));
    }

    // --------------------------------------------------------------- limites

    @Test
    void barraCriacaoAcimaDoLimiteDePrincipais() throws SQLException {
        int existentes = Financas.contar(c, EU, GASTO, true);
        for (int i = existentes; i < Financas.MAX_RAIZ; i++) {
            Financas.categoriaId(c, EU, "Extra " + i, GASTO);
        }
        assertEquals(Financas.MAX_RAIZ, Financas.contar(c, EU, GASTO, true));

        assertThrows(IllegalArgumentException.class,
                () -> Financas.categoriaId(c, EU, "Estourou", GASTO));
    }

    @Test
    void barraCriacaoAcimaDoLimiteDeSubcategorias() throws SQLException {
        for (int i = 0; i < Financas.MAX_SUB; i++) {
            Financas.categoriaId(c, EU, "Alimentação > Sub " + i, GASTO);
        }
        assertThrows(IllegalArgumentException.class,
                () -> Financas.categoriaId(c, EU, "Alimentação > Estourou", GASTO));
    }

    @Test
    void limiteDeGastoNaoConsomeOLimiteDeGanho() throws SQLException {
        int existentes = Financas.contar(c, EU, GASTO, true);
        for (int i = existentes; i < Financas.MAX_RAIZ; i++) {
            Financas.categoriaId(c, EU, "Extra " + i, GASTO);
        }
        Financas.categoriaId(c, EU, "Aluguel recebido", GANHO);   // não deve estourar
    }

    // ------------------------------------------------------------ gerenciar

    @Test
    void renomeiaCategoriaMantendoOsLancamentos() throws SQLException {
        gasto(EU, "50", "Lazer", HOJE);

        Financas.renomear(c, EU, "Lazer", "Diversão", GASTO);

        assertEquals("Diversão", resumoDoMes(EU).porDespesa().get(0).categoria());
        assertEquals(5_000, resumoDoMes(EU).despesas());
    }

    @Test
    void renomearRecusaNomeJaUsadoNoMesmoNivel() {
        assertThrows(IllegalArgumentException.class,
                () -> Financas.renomear(c, EU, "Lazer", "Transporte", GASTO));
    }

    @Test
    void renomearRecusaCategoriaInexistente() {
        assertThrows(IllegalArgumentException.class,
                () -> Financas.renomear(c, EU, "Inexistente", "Nova", GASTO));
    }

    @Test
    void excluiCategoriaSemLancamentos() throws SQLException {
        Financas.excluir(c, EU, "Lazer", GASTO);

        assertFalse(Financas.caminhos(c, EU, GASTO, "", 25).contains("Lazer"));
    }

    @Test
    void excluirLevaJuntoAsSubcategorias() throws SQLException {
        Financas.categoriaId(c, EU, "Lazer > Cinema", GASTO);

        Financas.excluir(c, EU, "Lazer", GASTO);

        assertFalse(Financas.caminhos(c, EU, GASTO, "", 25).contains("Lazer > Cinema"));
    }

    @Test
    void excluirRecusaCategoriaComLancamento() throws SQLException {
        gasto(EU, "50", "Lazer", HOJE);

        assertThrows(IllegalArgumentException.class, () -> Financas.excluir(c, EU, "Lazer", GASTO));
    }

    @Test
    void excluirRecusaQuandoASubcategoriaTemLancamento() throws SQLException {
        gasto(EU, "50", "Lazer > Cinema", HOJE);

        assertThrows(IllegalArgumentException.class, () -> Financas.excluir(c, EU, "Lazer", GASTO));
    }

    // ----------------------------------------------------------- lançamentos

    @Test
    void listaDoMaisRecenteParaOMaisAntigo() throws SQLException {
        gasto(EU, "10", "Alimentação", HOJE.minusDays(2));
        gasto(EU, "20", "Transporte", HOJE);

        List<Lancamento> lista =
                Financas.lancamentos(c, EU, Periodo.de("mes", HOJE), 20, 0);

        assertEquals(2, lista.size());
        assertEquals(HOJE, lista.get(0).data());
        assertEquals("Transporte", lista.get(0).categoria());
        assertEquals(-2_000, lista.get(0).centavos());
    }

    @Test
    void listaMostraOCaminhoCompletoDaSubcategoria() throws SQLException {
        gasto(EU, "10", "Alimentação > Mercado", HOJE);

        assertEquals("Alimentação > Mercado",
                Financas.lancamentos(c, EU, Periodo.de("mes", HOJE), 20, 0).get(0).categoria());
    }

    @Test
    void listaRespeitaOLimite() throws SQLException {
        for (int i = 0; i < 5; i++) {
            gasto(EU, "10", "Alimentação", HOJE);
        }

        assertEquals(3, Financas.lancamentos(c, EU, Periodo.de("mes", HOJE), 3, 0).size());
    }

    @Test
    void offsetPulaOsPrimeirosSemRepetir() throws SQLException {
        for (int i = 0; i < 5; i++) {
            Financas.registrar(c, EU, -(i + 1) * 1_000L,
                    Financas.categoriaId(c, EU, "Lazer", GASTO), null, HOJE);
        }
        Periodo mes = Periodo.de("mes", HOJE);

        List<Long> pagina1 = Financas.lancamentos(c, EU, mes, 2, 0)
                .stream().map(Lancamento::id).toList();
        List<Long> pagina2 = Financas.lancamentos(c, EU, mes, 2, 2)
                .stream().map(Lancamento::id).toList();

        assertEquals(2, pagina1.size());
        assertEquals(2, pagina2.size());
        assertTrue(pagina1.stream().noneMatch(pagina2::contains));
    }

    @Test
    void offsetAlemDoFimVemVazio() throws SQLException {
        gasto(EU, "10", "Lazer", HOJE);

        assertTrue(Financas.lancamentos(c, EU, Periodo.de("mes", HOJE), 20, 50).isEmpty());
    }

    @Test
    void listaNaoMostraLancamentoDeOutroUsuario() throws SQLException {
        gasto(OUTRO, "10", "Alimentação", HOJE);

        assertTrue(Financas.lancamentos(c, EU, Periodo.de("mes", HOJE), 20, 0).isEmpty());
    }

    // -------------------------------------------------------------- remoção

    @Test
    void removeApenasOProprioLancamento() throws SQLException {
        long id = Financas.registrar(c, EU, -5_000,
                Financas.categoriaId(c, EU, "Lazer", GASTO), null, HOJE);

        assertFalse(Financas.remover(c, OUTRO, id));
        assertTrue(Financas.remover(c, EU, id));
        assertTrue(resumoDoMes(EU).vazio());
    }

    // ------------------------------------------------------ acumulado e média

    @Test
    void acumuladoDaCategoriaSomaAsSubcategorias() throws SQLException {
        gasto(EU, "100", "Alimentação > Mercado", HOJE);
        gasto(EU, "40", "Alimentação", HOJE);
        gasto(EU, "999", "Transporte", HOJE);

        long id = Financas.categoriaId(c, EU, "Alimentação > Mercado", GASTO);

        assertEquals(14_000, Financas.totalDaCategoria(
                c, EU, id, Periodo.de("mes", HOJE), false));
    }

    @Test
    void valorTipicoExigeHistoricoMinimo() throws SQLException {
        long id = Financas.categoriaId(c, EU, "Lazer", GASTO);
        gasto(EU, "10", "Lazer", HOJE);
        gasto(EU, "10", "Lazer", HOJE);

        assertEquals(0, Financas.valorTipico(c, EU, id, HOJE));   // só 2 lançamentos

        gasto(EU, "10", "Lazer", HOJE);
        assertEquals(1_000, Financas.valorTipico(c, EU, id, HOJE));
    }

    @Test
    void valorTipicoIgnoraLancamentoAntigo() throws SQLException {
        long id = Financas.categoriaId(c, EU, "Lazer", GASTO);
        for (int i = 0; i < 3; i++) {
            gasto(EU, "500", "Lazer", HOJE.minusDays(200));
        }

        assertEquals(0, Financas.valorTipico(c, EU, id, HOJE));
    }

    // ------------------------------------------------------------- sugestões

    @Test
    void sugereCategoriaParecidaComOErroDeDigitacao() throws SQLException {
        assertEquals("Transporte", Financas.parecida(c, EU, "Transprte", GASTO).orElse(null));
    }

    @Test
    void naoSugereNadaQuandoONomeEhMuitoDiferente() throws SQLException {
        assertTrue(Financas.parecida(c, EU, "Astronomia", GASTO).isEmpty());
    }

    @Test
    void erroDeCategoriaInexistenteTrazSugestao() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Financas.renomear(c, EU, "Transprte", "Locomoção", GASTO));

        assertTrue(e.getMessage().contains("Transporte"));
    }

    // ------------------------------------------------------------- edição

    @Test
    void atualizaValorCategoriaEData() throws SQLException {
        long id = Financas.registrar(c, EU, -5_000,
                Financas.categoriaId(c, EU, "Lazer", GASTO), "cinema", HOJE);

        Financas.atualizar(c, EU, id, -7_500,
                Financas.categoriaId(c, EU, "Alimentação", GASTO), "jantar", HOJE.minusDays(1));

        Lancamento l = Financas.buscar(c, EU, id).orElseThrow();
        assertEquals(-7_500, l.centavos());
        assertEquals("Alimentação", l.categoria());
        assertEquals("jantar", l.descricao());
        assertEquals(HOJE.minusDays(1), l.data());
    }

    @Test
    void naoEditaLancamentoDeOutroUsuario() throws SQLException {
        long id = Financas.registrar(c, EU, -5_000,
                Financas.categoriaId(c, EU, "Lazer", GASTO), null, HOJE);

        assertFalse(Financas.atualizar(c, OUTRO, id, -1, 1, null, HOJE));
        assertTrue(Financas.buscar(c, OUTRO, id).isEmpty());
        assertEquals(-5_000, Financas.buscar(c, EU, id).orElseThrow().centavos());
    }

    // ----------------------------------------------------------- recorrentes

    @Test
    void lancaTodosOsFixosDeUmaVez() throws SQLException {
        Financas.adicionarRecorrente(c, EU, -150_000,
                Financas.categoriaId(c, EU, "Moradia", GASTO), "aluguel");
        Financas.adicionarRecorrente(c, EU, 300_000,
                Financas.categoriaId(c, EU, "Salário", GANHO), null);

        assertEquals(2, Financas.lancarRecorrentes(c, EU, HOJE));

        Resumo r = resumoDoMes(EU);
        assertEquals(150_000, r.despesas());
        assertEquals(300_000, r.ganhos());
    }

    @Test
    void fixoDeUmUsuarioNaoLancaParaOutro() throws SQLException {
        Financas.adicionarRecorrente(c, EU, -150_000,
                Financas.categoriaId(c, EU, "Moradia", GASTO), "aluguel");

        assertEquals(0, Financas.lancarRecorrentes(c, OUTRO, HOJE));
        assertTrue(resumoDoMes(OUTRO).vazio());
    }

    @Test
    void removeFixoSemMexerNosLancamentosJaFeitos() throws SQLException {
        long id = Financas.adicionarRecorrente(c, EU, -150_000,
                Financas.categoriaId(c, EU, "Moradia", GASTO), "aluguel");
        Financas.lancarRecorrentes(c, EU, HOJE);

        assertTrue(Financas.removerRecorrente(c, EU, id));
        assertTrue(Financas.recorrentes(c, EU).isEmpty());
        assertEquals(150_000, resumoDoMes(EU).despesas());
    }

    @Test
    void naoRemoveFixoDeOutroUsuario() throws SQLException {
        long id = Financas.adicionarRecorrente(c, EU, -1_000,
                Financas.categoriaId(c, EU, "Moradia", GASTO), null);

        assertFalse(Financas.removerRecorrente(c, OUTRO, id));
    }

    // -------------------------------------------------------------- períodos

    @Test
    void mesVaiDoPrimeiroAoUltimoDia() {
        Periodo p = Periodo.de("mes", HOJE);

        assertEquals(LocalDate.of(2026, 8, 1), p.inicio());
        assertEquals(LocalDate.of(2026, 8, 31), p.fim());
    }

    @Test
    void semanaVaiDeSegundaADomingo() {
        Periodo p = Periodo.de("semana", HOJE);

        assertEquals(LocalDate.of(2026, 8, 3), p.inicio());
        assertEquals(LocalDate.of(2026, 8, 9), p.fim());
    }

    @Test
    void mesAnteriorRespeitaTamanhoDoMes() {
        Periodo p = Periodo.de("mes", LocalDate.of(2026, 3, 15)).anterior();

        assertEquals(LocalDate.of(2026, 2, 1), p.inicio());
        assertEquals(LocalDate.of(2026, 2, 28), p.fim());
    }

    @Test
    void anoAnteriorAtravessaAVirada() {
        Periodo p = Periodo.de("ano", HOJE).anterior();

        assertEquals(LocalDate.of(2025, 1, 1), p.inicio());
        assertEquals(LocalDate.of(2025, 12, 31), p.fim());
    }

    // -------------------------------------------------------------- variação

    // ----------------------------------------------------------------- contas

    @Test
    void mesmaIdentidadeDevolveMesmaConta() throws SQLException {
        String conta = Financas.conta(c, "discord", "999");

        assertEquals(conta, Financas.conta(c, "discord", "999"));
        assertFalse(conta.equals(Financas.conta(c, "discord", "888")));
    }

    @Test
    void mesmoIdEmProvedorDiferenteEhOutraConta() throws SQLException {
        assertFalse(Financas.conta(c, "discord", "999").equals(Financas.conta(c, "telegram", "999")));
    }

    @Test
    void vincularLigaNovaIdentidadeAContaExistente() throws SQLException {
        String conta = Financas.conta(c, "discord", "999");

        Financas.vincular(c, conta, "google", "eu@exemplo.com");

        assertEquals(conta, Financas.conta(c, "google", "eu@exemplo.com"));
    }

    @Test
    void vincularIdentidadeDeOutraContaEhRecusado() throws SQLException {
        String minha = Financas.conta(c, "discord", "999");
        Financas.conta(c, "google", "outro@exemplo.com");

        assertThrows(IllegalArgumentException.class,
                () -> Financas.vincular(c, minha, "google", "outro@exemplo.com"));
    }

    @Test
    void migracaoMoveDadosDoIdDoDiscordParaConta() throws SQLException {
        // EU e OUTRO foram gravados como antes: usuario = id do Discord
        gasto(EU, "50", "Lazer", HOJE);

        Financas.migrarParaContas(c);
        Financas.migrarParaContas(c);   // idempotente

        String conta = Financas.conta(c, "discord", EU);
        assertEquals(5_000, resumoDoMes(conta).despesas());
        assertEquals(0, resumoDoMes(EU).despesas());
        assertFalse(conta.equals(Financas.conta(c, "discord", OUTRO)));
    }
}
