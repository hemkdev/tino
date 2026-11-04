package tino.discord;

import tino.nucleo.Evento;
import tino.nucleo.Lancamento;
import tino.nucleo.Linha;
import tino.nucleo.Pedido;
import tino.nucleo.Periodo;
import tino.nucleo.Resumo;
import tino.nucleo.Tino;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualTest {

    private static final Periodo AGOSTO = new Periodo(
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), ChronoUnit.MONTHS);
    private static final LocalDate HOJE = LocalDate.of(2026, 8, 7);

    @Test
    void barraEncheProporcionalmenteAMaiorLinha() {
        assertEquals("▰▰▰▰▰▰▰▰", Visual.barra(100, 100));
        assertEquals("▰▰▰▰▱▱▱▱", Visual.barra(50, 100));
    }

    @Test
    void barraNuncaFicaVaziaNemEstoura() {
        assertTrue(Visual.barra(1, 1_000_000).startsWith("▰"));
        assertEquals(8, Visual.barra(999, 100).replace("▱", "").length());
    }

    @Test
    void barraSemBaseNaoQuebra() {
        assertEquals("▱▱▱▱▱▱▱▱", Visual.barra(0, 0));
    }

    @Test
    void subcategoriaHerdaOEmojiDoPai() {
        assertEquals(Visual.emoji("Alimentação"), Visual.emoji("Alimentação > Mercado"));
    }

    @Test
    void categoriaDesconhecidaGanhaEmojiGenerico() {
        assertEquals("🔸", Visual.emoji("Terapia"));
    }

    @Test
    void resumoVazioOrientaEmVezDeMostrarTabela() {
        MessageEmbed embed = Visual.resumo(AGOSTO,
                new Resumo(0, 0, List.of(), List.of()),
                new Resumo(0, 0, List.of(), List.of()), null);

        assertTrue(embed.getDescription().contains("50 mercado"));   // ensina em vez de tabular
    }

    @Test
    void resumoMostraCategoriasESaldo() {
        Resumo atual = new Resumo(300_000, 80_000,
                List.of(new Linha("Alimentação", 50_000),
                        new Linha("Transporte", 30_000)),
                List.of(new Linha("Salário", 300_000)));
        Resumo anterior = new Resumo(0, 40_000, List.of(), List.of());

        MessageEmbed embed = Visual.resumo(AGOSTO, atual, anterior, null);

        assertTrue(embed.getDescription().contains("Alimentação"));
        assertTrue(embed.getDescription().contains("62,5%"));      // 500 de 800
        assertEquals("+R$ 2.200,00", embed.getFields().get(2).getValue());
        assertTrue(embed.getFooter().getText().contains("+100,0%"));
        assertTrue(embed.getFooter().getText().contains("—"));     // ganhos sem base anterior
    }

    @Test
    void registroDeGastoEDeGanhoUsamCoresDiferentes() {
        MessageEmbed gasto = Visual.registro(-5_000, "Lazer", null, HOJE, 12, 0, null);
        MessageEmbed ganho = Visual.registro(5_000, "Salário", null, HOJE, 13, 0, null);

        assertNotNull(gasto.getColor());
        assertTrue(gasto.getColor().getRed() > gasto.getColor().getGreen());
        assertTrue(ganho.getColor().getGreen() > ganho.getColor().getRed());
        assertTrue(gasto.getFooter().getText().contains("#12"));
    }

    @Test
    void registroMostraDescricaoQuandoExiste() {
        assertTrue(Visual.registro(-5_000, "Lazer", "cinema", HOJE, 1, 0, null)
                .getDescription().contains("cinema"));
    }

    @Test
    void listaDeLancamentosMostraIdESinal() {
        MessageEmbed embed = Visual.lancamentos(AGOSTO, List.of(
                new Lancamento(18, LocalDate.of(2026, 8, 7), -5_000,
                        "Alimentação > Mercado", "pão")), 0, false);

        assertTrue(embed.getDescription().contains("#18"));
        assertTrue(embed.getDescription().contains("-R$ 50,00"));
        assertTrue(embed.getDescription().contains("pão"));
    }

    @Test
    void listaMostraAFaixaDaPaginaAtual() {
        List<Lancamento> cheia = java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> new Lancamento(i, LocalDate.of(2026, 8, 7), -100, "Lazer", null))
                .toList();

        assertTrue(Visual.lancamentos(AGOSTO, cheia, 20, true).getFooter().getText()
                .contains("21–40"));
    }

    @Test
    void resumoMostraRitmoQuandoHa() {
        Resumo atual = new Resumo(0, 70_000,
                List.of(new Linha("Lazer", 70_000)), List.of());

        MessageEmbed embed = Visual.resumo(AGOSTO, atual, atual, new Tino.Ritmo(10_000, 310_000));

        String ritmo = embed.getFields().getLast().getValue();
        assertTrue(ritmo.contains("R$ 100,00 por dia"));
        assertTrue(ritmo.contains("R$ 3.100,00"));
        assertTrue(ritmo.contains("31/08"));
    }

    @Test
    void registroMostraAcumuladoDoMes() {
        assertTrue(Visual.registro(-5_000, "Lazer", null, HOJE, 1, 84_000, null)
                .getDescription().contains("R$ 840,00"));
    }

    @Test
    void foraDoNormalPerguntaComOValorDeReferencia() {
        String d = Visual.foraDoNormal(new Evento.ForaDoNormal(
                new Pedido(-480_000, "Moradia", null, HOJE), 180_000)).getDescription();

        assertTrue(d.contains("R$ 4.800,00 em Moradia?"));
        assertTrue(d.contains("Seu normal é R$ 1.800,00"));
    }

    @Test
    void confirmacaoTemTaCertoECorrigirComIdCurto() {
        var botoes = Visual.confirmacao("1".repeat(20), "999999").getButtons();

        assertEquals("Tá certo", botoes.get(0).getLabel());
        assertEquals("Corrigir", botoes.get(1).getLabel());
        assertTrue(botoes.get(0).getId().length() <= 100);
    }

    @Test
    void registroAvisaCategoriaParecidaRecemCriada() {
        assertTrue(Visual.registro(-5_000, "Transprte", null, HOJE, 1, 0, "Transporte")
                .getDescription().contains("Transporte"));
    }

    @Test
    void botaoDesfazerCarregaDonoELancamento() {
        String id = Visual.acoesDoRegistro("111", 42).getButtons().get(0).getId();

        assertEquals("desfazer:111:42", id);
    }

    @Test
    void naPrimeiraPaginaSoOAvancarFicaAtivo() {
        var botoes = Visual.paginacao("111", "mes", 0, true, 20).getButtons();

        assertTrue(botoes.get(0).isDisabled());    // ◀ Anterior
        assertTrue(!botoes.get(1).isDisabled());   // Próximo ▶
    }

    @Test
    void naUltimaPaginaSoOVoltarFicaAtivo() {
        var botoes = Visual.paginacao("111", "mes", 20, false, 20).getButtons();

        assertTrue(!botoes.get(0).isDisabled());
        assertTrue(botoes.get(1).isDisabled());
    }

    @Test
    void botoesDePaginacaoLevamOffsetEPeriodo() {
        var botoes = Visual.paginacao("111", "semana", 20, true, 20).getButtons();

        assertEquals("pag:111:semana:0", botoes.get(0).getId());
        assertEquals("pag:111:semana:40", botoes.get(1).getId());
    }

    @Test
    void voltarNaoGeraOffsetNegativo() {
        assertEquals("pag:111:mes:0",
                Visual.paginacao("111", "mes", 10, true, 20).getButtons().get(0).getId());
    }

    @Test
    void idDeBotaoCabeNoLimiteDoDiscord() {
        // snowflake do Discord tem 17–19 dígitos
        String longo = "123456789012345678";

        assertTrue(Visual.acoesDoRegistro(longo, Long.MAX_VALUE).getButtons().get(0).getId().length() <= 100);
        assertTrue(Visual.paginacao(longo, "semana", 999_999, true, 20)
                .getButtons().get(1).getId().length() <= 100);
    }

    @Test
    void arvoreDeCategoriasIndentaSubcategoria() {
        MessageEmbed embed = Visual.categorias(new Tino.Categorias(
                List.of("Alimentação", "Alimentação > Mercado"), 1, 1,
                List.of("Salário"), 1, 0));

        assertTrue(embed.getFields().get(0).getValue().contains("↳ Mercado"));
        assertTrue(embed.getFields().get(1).getValue().contains("Salário"));
    }

    @Test
    void variacaoSemBaseDeComparacaoNaoDividePorZero() {
        assertEquals("—", Visual.variacao(1_000, 0));
    }

    @Test
    void variacaoMostraSinal() {
        assertEquals("+50,0%", Visual.variacao(150, 100));
        assertEquals("-25,0%", Visual.variacao(75, 100));
    }

    // ------------------------------------------------------------ personagem

    private static String broto(MessageEmbed e) {
        return e.getThumbnail().getUrl();
    }

    @Test
    void registroMostraOTinoComemorandoOuDormindo() {
        String url = broto(Visual.registro(-5_000, "Lazer", null, HOJE, 1, 0, null));
        assertTrue(url.endsWith("/sucesso.gif") || url.endsWith("/dormindo.gif"));
    }

    @Test
    void foraDoNormalMostraOTinoAlerta() {
        assertTrue(broto(Visual.foraDoNormal(new Evento.ForaDoNormal(
                new Pedido(-480_000, "Moradia", null, HOJE), 180_000))).endsWith("/alerta.gif"));
    }

    @Test
    void avisoEErroMostramOTinoSemEntender() {
        assertTrue(broto(Visual.aviso("não entendi")).endsWith("/erro.gif"));
        assertTrue(broto(Visual.erro("falhou")).endsWith("/erro.gif"));
    }

    @Test
    void boasVindasMostraOTinoEsperando() {
        assertTrue(broto(Visual.boasVindas()).endsWith("/ocioso.gif"));
    }
}
