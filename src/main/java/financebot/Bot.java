package financebot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Bot extends ListenerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(Bot.class);
    private static final int LIMITE_LISTA = 20;
    private static final String PADRAO = "Outros";

    // ponytail: uma conexão só, serializada por lock. Um bot pessoal não tem
    // concorrência que justifique pool; se tiver, trocar por HikariCP.
    private final Connection conexao;

    private Bot(Connection conexao) {
        this.conexao = conexao;
    }

    public static void main(String[] args) throws Exception {
        String token = System.getenv("DISCORD_TOKEN");
        if (token == null || token.isBlank()) {
            System.err.println("defina DISCORD_TOKEN");
            System.exit(1);
        }

        String caminho = System.getenv().getOrDefault("BANCO_CAMINHO", "financebot.db");
        Connection conexao = DriverManager.getConnection("jdbc:sqlite:" + caminho);
        try (Statement s = conexao.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA busy_timeout=5000");
        }
        Financas.criarTabelas(conexao);

        // DIRECT_MESSAGES não é privilegiada, e em DM o conteúdo da mensagem vem
        // preenchido sem precisar da intent de conteúdo.
        JDA jda = JDABuilder.createLight(token, GatewayIntent.DIRECT_MESSAGES)
                .addEventListeners(new Bot(conexao))
                .build()
                .awaitReady();
        registrarComandos(jda);
        LOG.info("bot no ar");
    }

    private static void registrarComandos(JDA jda) {
        OptionData descricao = new OptionData(OptionType.STRING, "descricao", "Opcional");
        OptionData data = new OptionData(OptionType.STRING, "data", "DD/MM ou AAAA-MM-DD (padrão: hoje)");
        OptionData periodo = new OptionData(OptionType.STRING, "periodo", "Padrão: mês")
                .addChoice("mês", "mes").addChoice("semana", "semana").addChoice("ano", "ano");

        List<CommandData> comandos = List.of(
                Commands.slash("gasto", "Registra uma despesa").addOptions(
                        new OptionData(OptionType.STRING, "valor", "Ex.: 50 ou 1.234,56", true),
                        new OptionData(OptionType.STRING, "categoria",
                                "Padrão: Outros", false, true),
                        descricao, data),

                Commands.slash("ganho", "Registra uma receita").addOptions(
                        new OptionData(OptionType.STRING, "valor", "Ex.: 3000", true),
                        new OptionData(OptionType.STRING, "categoria", "Padrão: Outros", false, true),
                        descricao, data),

                Commands.slash("lancar", "Vários de uma vez: 50 mercado, 30 uber, +3000 salário")
                        .addOptions(new OptionData(OptionType.STRING, "texto",
                                "Ex.: 50 mercado, 30 uber", true)),

                Commands.slash("resumo", "Por categoria, com variação e projeção").addOptions(periodo),

                Commands.slash("lancamentos", "Lista os lançamentos do período com seus ids")
                        .addOptions(periodo),

                Commands.slash("exportar", "Baixa os lançamentos do período em CSV")
                        .addOptions(periodo),

                Commands.slash("remover", "Apaga um lançamento").addOptions(
                        new OptionData(OptionType.INTEGER, "id", "Número mostrado em /lancamentos", true)),

                Commands.slash("ajuda", "Como usar o bot"),

                Commands.slash("categoria", "Gerencia suas categorias").addSubcommands(
                        new SubcommandData("listar", "Mostra suas categorias e quanto resta do limite"),
                        new SubcommandData("criar", "Cria uma categoria ou subcategoria").addOptions(
                                tipoObrigatorio(),
                                new OptionData(OptionType.STRING, "nome",
                                        "Ex.: Pet ou Alimentação > Mercado", true)),
                        new SubcommandData("renomear", "Muda o nome de uma categoria").addOptions(
                                tipoObrigatorio(),
                                new OptionData(OptionType.STRING, "categoria", "Qual renomear", true, true),
                                new OptionData(OptionType.STRING, "nome", "Novo nome", true)),
                        new SubcommandData("excluir", "Apaga uma categoria sem lançamentos").addOptions(
                                tipoObrigatorio(),
                                new OptionData(OptionType.STRING, "categoria", "Qual excluir", true, true))),

                Commands.slash("fixo", "Aluguel, assinaturas e outros lançamentos de todo mês")
                        .addSubcommands(
                                new SubcommandData("listar", "Mostra seus lançamentos fixos"),
                                new SubcommandData("adicionar", "Cadastra um fixo").addOptions(
                                        new OptionData(OptionType.STRING, "valor",
                                                "Use + para ganho: +3000", true),
                                        new OptionData(OptionType.STRING, "categoria", "Categoria", true, true),
                                        descricao),
                                new SubcommandData("remover", "Tira um fixo da lista").addOptions(
                                        new OptionData(OptionType.INTEGER, "id",
                                                "Número mostrado em /fixo listar", true)),
                                new SubcommandData("lancar", "Registra todos os fixos de uma vez")));

        String guild = System.getenv("DISCORD_GUILD_ID");
        if (guild != null && !guild.isBlank()) {
            // Registro por guild aparece na hora — útil ao mexer nas assinaturas.
            jda.getGuildById(guild).updateCommands().addCommands(comandos).queue();
            LOG.info("comandos registrados na guild {}", guild);
            return;
        }
        // Global: única forma de os comandos existirem em conversa privada.
        comandos.forEach(comando -> comando
                .setIntegrationTypes(IntegrationType.ALL)
                .setContexts(InteractionContextType.ALL));
        jda.updateCommands().addCommands(comandos).queue();
        LOG.info("comandos registrados globalmente");
    }

    private static OptionData tipoObrigatorio() {
        return new OptionData(OptionType.STRING, "tipo", "Categoria de gasto ou de ganho", true)
                .addChoice("gasto", Financas.GASTO)
                .addChoice("ganho", Financas.GANHO);
    }

    // ------------------------------------------------------------- interação

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent evento) {
        // Em DM a conversa já é privada e a resposta fica no histórico. Em servidor,
        // ephemeral evita expor gasto para o canal inteiro.
        evento.deferReply(evento.isFromGuild()).queue();
        try {
            String usuario = evento.getUser().getId();

            if (evento.getName().equals("exportar")) {
                exportar(evento, usuario);
                return;
            }

            Resposta resposta;
            synchronized (conexao) {
                Financas.prepararUsuario(conexao, usuario);
                resposta = switch (evento.getName()) {
                    case "gasto" -> registrar(evento, usuario, false);
                    case "ganho" -> registrar(evento, usuario, true);
                    case "lancar" -> lote(usuario,
                            evento.getOption("texto").getAsString(), LocalDate.now());
                    case "resumo" -> Resposta.de(resumo(usuario, periodo(evento)));
                    case "lancamentos" -> pagina(usuario, periodo(evento), 0);
                    case "remover" -> Resposta.de(remover(evento, usuario));
                    case "categoria" -> Resposta.de(categoria(evento, usuario));
                    case "fixo" -> Resposta.de(fixo(evento, usuario));
                    case "ajuda" -> Resposta.de(Visual.ajuda());
                    default -> Resposta.de(Visual.erro("comando desconhecido"));
                };
            }
            responder(evento, resposta);
        } catch (IllegalArgumentException e) {
            evento.getHook().sendMessageEmbeds(Visual.aviso(e.getMessage())).queue();
        } catch (Exception e) {
            LOG.error("falha em /{}", evento.getName(), e);   // sem valores nem descrições no log
            evento.getHook().sendMessageEmbeds(Visual.erro("deu erro aqui, tenta de novo")).queue();
        }
    }

    private void responder(SlashCommandInteractionEvent evento, Resposta resposta) {
        var acao = evento.getHook().sendMessageEmbeds(resposta.embed());
        if (resposta.botoes() != null) {
            acao = acao.setComponents(resposta.botoes());
        }
        acao.queue();
    }

    /** Texto solto na DM: "50 mercado" vira lançamento sem precisar de barra. */
    @Override
    public void onMessageReceived(MessageReceivedEvent evento) {
        if (evento.getAuthor().isBot() || evento.isFromGuild()) {
            return;
        }
        String texto = evento.getMessage().getContentRaw().trim();
        if (!Entrada.pareceLancamento(texto)) {
            return;   // conversa solta não vira lançamento
        }
        try {
            String usuario = evento.getAuthor().getId();
            Resposta resposta;
            synchronized (conexao) {
                Financas.prepararUsuario(conexao, usuario);
                resposta = lote(usuario, texto, LocalDate.now());
            }
            var acao = evento.getMessage().replyEmbeds(resposta.embed());
            if (resposta.botoes() != null) {
                acao = acao.setComponents(resposta.botoes());
            }
            acao.queue();
        } catch (IllegalArgumentException e) {
            evento.getMessage().replyEmbeds(Visual.aviso(e.getMessage())).queue();
        } catch (Exception e) {
            LOG.error("falha ao interpretar mensagem", e);
            evento.getMessage().replyEmbeds(Visual.erro("deu erro aqui, tenta de novo")).queue();
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent evento) {
        String[] partes = evento.getComponentId().split(":");
        if (partes.length < 3 || !partes[1].equals(evento.getUser().getId())) {
            evento.reply("Esse botão não é seu.").setEphemeral(true).queue();
            return;
        }
        try {
            switch (partes[0]) {
                case "desfazer" -> {
                    boolean removido;
                    synchronized (conexao) {
                        removido = Financas.remover(conexao, partes[1], Long.parseLong(partes[2]));
                    }
                    evento.editMessageEmbeds(removido
                                    ? Visual.ok("Lançamento `#%s` desfeito.".formatted(partes[2]))
                                    : Visual.aviso("Esse lançamento já não existe."))
                            .setComponents()
                            .queue();
                }
                case "editar" -> {
                    Optional<Financas.Lancamento> alvo;
                    synchronized (conexao) {
                        alvo = Financas.buscar(conexao, partes[1], Long.parseLong(partes[2]));
                    }
                    if (alvo.isEmpty()) {
                        evento.reply("Esse lançamento já não existe.").setEphemeral(true).queue();
                        return;
                    }
                    evento.replyModal(Visual.edicao(partes[1], alvo.get())).queue();
                }
                case "pag" -> {
                    Resposta r;
                    synchronized (conexao) {
                        r = pagina(partes[1], partes[2], Integer.parseInt(partes[3]));
                    }
                    evento.editMessageEmbeds(r.embed()).setComponents(r.botoes()).queue();
                }
                default -> evento.deferEdit().queue();
            }
        } catch (Exception e) {
            LOG.error("falha no botão {}", partes[0], e);
            evento.reply("deu erro aqui, tenta de novo").setEphemeral(true).queue();
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent evento) {
        String[] partes = evento.getModalId().split(":");
        if (partes.length < 3 || !partes[1].equals(evento.getUser().getId())) {
            return;
        }
        evento.deferEdit().queue();
        try {
            long id = Long.parseLong(partes[2]);
            String usuario = partes[1];

            long centavos = Dinheiro.paraCentavos(campo(evento, "valor"));
            String caminho = campo(evento, "categoria");
            String novaDescricao = campo(evento, "descricao");
            LocalDate novaData = Financas.parseData(campo(evento, "data"), LocalDate.now());

            MessageEmbed embed;
            synchronized (conexao) {
                Financas.Lancamento antes = Financas.buscar(conexao, usuario, id).orElseThrow(
                        () -> new IllegalArgumentException("esse lançamento já não existe"));
                boolean ganho = antes.centavos() > 0;

                long categoriaId = Financas.categoriaId(conexao, usuario, caminho,
                        ganho ? Financas.GANHO : Financas.GASTO);
                Financas.atualizar(conexao, usuario, id, ganho ? centavos : -centavos,
                        categoriaId, novaDescricao, novaData);

                embed = Visual.registro(ganho ? centavos : -centavos, caminho, novaDescricao,
                        novaData, id, 0, 0, null);
            }
            evento.getHook().editOriginalEmbeds(embed).queue();
        } catch (IllegalArgumentException e) {
            evento.getHook().sendMessageEmbeds(Visual.aviso(e.getMessage())).setEphemeral(true).queue();
        } catch (Exception e) {
            LOG.error("falha ao editar", e);
            evento.getHook().sendMessageEmbeds(Visual.erro("não consegui editar")).setEphemeral(true).queue();
        }
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent evento) {
        if (!evento.getFocusedOption().getName().equals("categoria")) {
            return;
        }
        try {
            List<String> nomes;
            synchronized (conexao) {
                Financas.prepararUsuario(conexao, evento.getUser().getId());
                nomes = Financas.caminhos(conexao, evento.getUser().getId(),
                        tipoDoContexto(evento), evento.getFocusedOption().getValue(), 25);
            }
            evento.replyChoiceStrings(nomes).queue();
        } catch (SQLException e) {
            evento.replyChoiceStrings().queue();
        }
    }

    /** Em /gasto e /ganho o tipo vem do comando; em /categoria, da opção escolhida. */
    private static String tipoDoContexto(CommandAutoCompleteInteractionEvent evento) {
        return switch (evento.getName()) {
            case "gasto" -> Financas.GASTO;
            case "ganho" -> Financas.GANHO;
            case "fixo" -> null;   // fixo aceita os dois
            default -> {
                OptionMapping tipo = evento.getOption("tipo");
                yield tipo == null ? null : tipo.getAsString();
            }
        };
    }

    // -------------------------------------------------------------- comandos

    private Resposta registrar(SlashCommandInteractionEvent evento, String usuario, boolean ganho)
            throws SQLException {
        String categoria = textoOu(evento, "categoria", PADRAO);
        String descricao = texto(evento, "descricao");
        long valor = Dinheiro.paraCentavos(evento.getOption("valor").getAsString());
        LocalDate data = Financas.parseData(texto(evento, "data"), LocalDate.now());

        return gravar(usuario, ganho ? valor : -valor, categoria, descricao, data);
    }

    /** Grava um lançamento e monta o embed com acumulado, alerta e aviso de categoria nova. */
    private Resposta gravar(String usuario, long centavos, String categoria,
                            String descricao, LocalDate data) throws SQLException {
        boolean ganho = centavos > 0;
        String tipo = ganho ? Financas.GANHO : Financas.GASTO;

        // Antes de criar: se não existe mas há uma parecida, vale avisar do provável engano.
        String parecida = Financas.caminhos(conexao, usuario, tipo, categoria, 1).isEmpty()
                ? Financas.parecida(conexao, usuario, categoria, tipo).orElse(null)
                : null;

        long categoriaId = Financas.categoriaId(conexao, usuario, categoria, tipo);
        long tipico = Financas.valorTipico(conexao, usuario, categoriaId, data);
        long id = Financas.registrar(conexao, usuario, centavos, categoriaId, descricao, data);

        long acumulado = Financas.totalDaCategoria(conexao, usuario, categoriaId,
                Financas.Periodo.de("mes", data), ganho);

        return new Resposta(
                Visual.registro(centavos, categoria, descricao, data, id, acumulado, tipico, parecida),
                Visual.acoesDoRegistro(usuario, id));
    }

    /** Vários lançamentos numa tacada, tanto por /lancar quanto por texto solto na DM. */
    private Resposta lote(String usuario, String texto, LocalDate hoje) throws SQLException {
        List<Entrada.Item> itens = Entrada.lista(texto);

        if (itens.size() == 1) {
            Entrada.Item item = itens.get(0);
            return gravar(usuario, item.ganho() ? item.centavos() : -item.centavos(),
                    item.categoria() == null ? PADRAO : item.categoria(), item.descricao(),
                    Financas.parseData(item.data(), hoje));
        }

        List<String> linhas = new ArrayList<>();
        long totalGasto = 0;
        long totalGanho = 0;
        for (Entrada.Item item : itens) {
            String categoria = item.categoria() == null ? PADRAO : item.categoria();
            long centavos = item.ganho() ? item.centavos() : -item.centavos();
            long categoriaId = Financas.categoriaId(conexao, usuario, categoria,
                    item.ganho() ? Financas.GANHO : Financas.GASTO);
            long id = Financas.registrar(conexao, usuario, centavos, categoriaId,
                    item.descricao(), Financas.parseData(item.data(), hoje));

            if (item.ganho()) {
                totalGanho += item.centavos();
            } else {
                totalGasto += item.centavos();
            }
            linhas.add("`#%d` %s **%s%s** · %s".formatted(id, Visual.emoji(categoria),
                    item.ganho() ? "+" : "-", Dinheiro.formatar(item.centavos()), categoria));
        }
        return Resposta.de(Visual.lote(linhas, totalGasto, totalGanho));
    }

    private MessageEmbed resumo(String usuario, String tipoPeriodo) throws SQLException {
        LocalDate hoje = LocalDate.now();
        Financas.Periodo periodo = Financas.Periodo.de(tipoPeriodo, hoje);
        return Visual.resumo(periodo,
                Financas.resumo(conexao, usuario, periodo),
                Financas.resumo(conexao, usuario, periodo.anterior()),
                hoje);
    }

    /** Uma página de lançamentos com os botões de navegação, quando fazem sentido. */
    private Resposta pagina(String usuario, String tipoPeriodo, int offset) throws SQLException {
        Financas.Periodo periodo = Financas.Periodo.de(tipoPeriodo, LocalDate.now());
        // Busca um a mais do que cabe: se vier, existe página seguinte.
        List<Financas.Lancamento> lista =
                Financas.lancamentos(conexao, usuario, periodo, LIMITE_LISTA + 1, offset);

        boolean temProxima = lista.size() > LIMITE_LISTA;
        if (temProxima) {
            lista = lista.subList(0, LIMITE_LISTA);
        }

        MessageEmbed embed = Visual.lancamentos(periodo, lista, offset, temProxima);
        return offset == 0 && !temProxima
                ? Resposta.de(embed)
                : new Resposta(embed,
                        Visual.paginacao(usuario, tipoPeriodo, offset, temProxima, LIMITE_LISTA));
    }

    private void exportar(SlashCommandInteractionEvent evento, String usuario) throws SQLException {
        Financas.Periodo periodo = Financas.Periodo.de(periodo(evento), LocalDate.now());
        List<Financas.Lancamento> lista;
        synchronized (conexao) {
            Financas.prepararUsuario(conexao, usuario);
            lista = Financas.lancamentos(conexao, usuario, periodo, Integer.MAX_VALUE, 0);
        }
        if (lista.isEmpty()) {
            evento.getHook().sendMessageEmbeds(Visual.aviso("Nada para exportar neste período.")).queue();
            return;
        }
        byte[] csv = Visual.csv(lista).getBytes(StandardCharsets.UTF_8);
        evento.getHook()
                .sendMessageEmbeds(Visual.ok("%d lançamentos de %s a %s.".formatted(
                        lista.size(), periodo.inicio(), periodo.fim())))
                .addFiles(FileUpload.fromData(csv,
                        "lancamentos-%s.csv".formatted(periodo.inicio())))
                .queue();
    }

    private MessageEmbed remover(SlashCommandInteractionEvent evento, String usuario)
            throws SQLException {
        long id = evento.getOption("id").getAsLong();
        return Financas.remover(conexao, usuario, id)
                ? Visual.ok("Lançamento `#%d` removido.".formatted(id))
                : Visual.aviso("Não achei o lançamento `#%d`.".formatted(id));
    }

    private MessageEmbed categoria(SlashCommandInteractionEvent evento, String usuario)
            throws SQLException {
        String tipo = texto(evento, "tipo");
        return switch (evento.getSubcommandName()) {
            case "criar" -> {
                String nome = evento.getOption("nome").getAsString();
                Financas.categoriaId(conexao, usuario, nome, tipo);
                yield Visual.ok("Categoria de %s criada: **%s**".formatted(tipo, nome));
            }
            case "renomear" -> {
                String antes = evento.getOption("categoria").getAsString();
                String depois = evento.getOption("nome").getAsString();
                Financas.renomear(conexao, usuario, antes, depois, tipo);
                yield Visual.ok("**%s** agora é **%s**.".formatted(antes, depois));
            }
            case "excluir" -> {
                String alvo = evento.getOption("categoria").getAsString();
                Financas.excluir(conexao, usuario, alvo, tipo);
                yield Visual.ok("**%s** excluída.".formatted(alvo));
            }
            default -> Visual.categorias(
                    Financas.caminhos(conexao, usuario, Financas.GASTO, "", 200),
                    Financas.contar(conexao, usuario, Financas.GASTO, true),
                    Financas.caminhos(conexao, usuario, Financas.GANHO, "", 200),
                    Financas.contar(conexao, usuario, Financas.GANHO, true),
                    Financas.contar(conexao, usuario, Financas.GASTO, false),
                    Financas.contar(conexao, usuario, Financas.GANHO, false));
        };
    }

    private MessageEmbed fixo(SlashCommandInteractionEvent evento, String usuario)
            throws SQLException {
        return switch (evento.getSubcommandName()) {
            case "adicionar" -> {
                Entrada.Item item = Entrada.item(evento.getOption("valor").getAsString());
                String categoria = evento.getOption("categoria").getAsString();
                long categoriaId = Financas.categoriaId(conexao, usuario, categoria,
                        item.ganho() ? Financas.GANHO : Financas.GASTO);
                Financas.adicionarRecorrente(conexao, usuario,
                        item.ganho() ? item.centavos() : -item.centavos(),
                        categoriaId, texto(evento, "descricao"));
                yield Visual.ok("Fixo cadastrado: **%s** em %s".formatted(
                        Dinheiro.formatar(item.centavos()), categoria));
            }
            case "remover" -> {
                long id = evento.getOption("id").getAsLong();
                yield Financas.removerRecorrente(conexao, usuario, id)
                        ? Visual.ok("Fixo `#%d` removido.".formatted(id))
                        : Visual.aviso("Não achei o fixo `#%d`.".formatted(id));
            }
            case "lancar" -> {
                int quantos = Financas.lancarRecorrentes(conexao, usuario, LocalDate.now());
                yield quantos == 0
                        ? Visual.aviso("Nenhum fixo cadastrado. Use `/fixo adicionar`.")
                        : Visual.ok("%d fixo(s) lançados hoje. Confira em `/lancamentos`."
                                .formatted(quantos));
            }
            default -> Visual.recorrentes(Financas.recorrentes(conexao, usuario));
        };
    }

    // ---------------------------------------------------------------- apoio

    /** Embed e, quando houver, a linha de botões que o acompanha. */
    private record Resposta(MessageEmbed embed, ActionRow botoes) {
        static Resposta de(MessageEmbed embed) {
            return new Resposta(embed, null);
        }
    }

    private static String periodo(SlashCommandInteractionEvent evento) {
        OptionMapping opcao = evento.getOption("periodo");
        return opcao == null ? "mes" : opcao.getAsString();
    }

    private static String texto(SlashCommandInteractionEvent evento, String nome) {
        OptionMapping opcao = evento.getOption(nome);
        return opcao == null ? null : opcao.getAsString();
    }

    private static String textoOu(SlashCommandInteractionEvent evento, String nome, String padrao) {
        String valor = texto(evento, nome);
        return valor == null || valor.isBlank() ? padrao : valor;
    }

    private static String campo(ModalInteractionEvent evento, String nome) {
        var valor = evento.getValue(nome);
        return valor == null ? null : valor.getAsString();
    }
}
