package tino.discord;

import tino.nucleo.Dinheiro;
import tino.nucleo.Entrada;
import tino.nucleo.Escala;
import tino.nucleo.Evento;
import tino.nucleo.Lancamento;
import tino.nucleo.Pedido;
import tino.nucleo.Tino;
import tino.nucleo.Tipo;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
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
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/** Canal Discord: traduz eventos do JDA em chamadas ao {@link Tino} e respostas em embed. */
public class Bot extends ListenerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(Bot.class);

    private final Tino tino;

    private Bot(Tino tino) {
        this.tino = tino;
    }

    /** {@code java -jar tino.jar} sobe o bot; {@code java -jar tino.jar migrar} só aplica as migrations. */
    public static void main(String[] args) throws Exception {
        String token;
        Tino tino;
        try {
            Map<String, String> env = System.getenv();
            String url = exigir(env, "DATABASE_URL");
            String usuario = exigir(env, "DATABASE_USER");
            String senha = exigir(env, "DATABASE_PASSWORD");
            if (args.length > 0 && args[0].equals("migrar")) {
                Tino.migrar(url, usuario, senha);
                LOG.info("migrations aplicadas");
                return;
            }
            token = exigir(env, "DISCORD_TOKEN");
            tino = Tino.abrir(url, usuario, senha);
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        // DIRECT_MESSAGES não é privilegiada, e em DM o conteúdo da mensagem vem
        // preenchido sem precisar da intent de conteúdo.
        JDA jda = JDABuilder.createLight(token, GatewayIntent.DIRECT_MESSAGES)
                // Ao lado da bolinha verde: mostra que está no ar e como começar.
                .setActivity(Activity.customStatus("Me manda: 50 mercado"))
                .addEventListeners(new Bot(tino))
                .build()
                .awaitReady();
        registrarComandos(jda);
        LOG.info("bot no ar");
    }

    static String exigir(Map<String, String> env, String nome) {
        String valor = env.get(nome);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("defina " + nome);
        }
        return valor;
    }

    private static void registrarComandos(JDA jda) {
        List<CommandData> comandos = comandos();

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

    static List<CommandData> comandos() {
        OptionData descricao = new OptionData(OptionType.STRING, "descricao", "Opcional");
        OptionData data = new OptionData(OptionType.STRING, "data", "DD/MM ou AAAA-MM-DD (padrão: hoje)");
        OptionData periodo = new OptionData(OptionType.STRING, "periodo", "Padrão: mês")
                .addChoice("hoje", "dia").addChoice("semana", "semana")
                .addChoice("mês", "mes").addChoice("ano", "ano");

        return List.of(
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
                Commands.slash("help", "Como usar o bot"),

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
    }

    private static OptionData tipoObrigatorio() {
        return new OptionData(OptionType.STRING, "tipo", "Categoria de gasto ou de ganho", true)
                .addChoice("gasto", "gasto")
                .addChoice("ganho", "ganho");
    }

    // ------------------------------------------------------------- interação

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent evento) {
        // Em DM a conversa já é privada e a resposta fica no histórico. Em servidor,
        // ephemeral evita expor gasto para o canal inteiro.
        evento.deferReply(evento.isFromGuild()).queue();
        try {
            String usuario = tino.conta("discord", evento.getUser().getId());

            if (evento.getName().equals("exportar")) {
                exportar(evento, usuario);
                return;
            }

            if (tino.preparar(usuario)) {
                // Quem começa pela barra também precisa saber como o Tino funciona.
                evento.getHook().sendMessageEmbeds(Visual.boasVindas()).queue();
            }
            List<Resposta> respostas = switch (evento.getName()) {
                case "gasto" -> List.of(registrar(evento, usuario, false));
                case "ganho" -> List.of(registrar(evento, usuario, true));
                case "lancar" -> lote(usuario, evento.getOption("texto").getAsString());
                default -> List.of(comando(evento, usuario));
            };
            respostas.forEach(r -> responder(evento, r));
        } catch (IllegalArgumentException e) {
            evento.getHook().sendMessageEmbeds(Visual.aviso(e.getMessage())).queue();
        } catch (Exception e) {
            String codigo = codigoDeErro();
            LOG.error("falha em /{} [{}]", evento.getName(), codigo, e);   // sem valores nem descrições no log
            evento.getHook().sendMessageEmbeds(Visual.erroInterno(codigo)).queue();
        }
    }

    /** Comandos de uma resposta só. */
    private Resposta comando(SlashCommandInteractionEvent evento, String usuario) throws SQLException {
        return switch (evento.getName()) {
            case "resumo" -> Resposta.de(resumo(usuario, periodo(evento)));
            case "lancamentos" -> pagina(usuario, periodo(evento), 0);
            case "remover" -> Resposta.de(remover(evento, usuario));
            case "categoria" -> Resposta.de(categoria(evento, usuario));
            case "fixo" -> Resposta.de(fixo(evento, usuario));
            case "ajuda", "help" -> Resposta.de(Visual.ajuda());
            default -> Resposta.de(Visual.erro("Não conheço esse comando. Digite `ajuda` para ver o que eu faço."));
        };
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
        try {
            String usuario = tino.conta("discord", evento.getAuthor().getId());
            boolean primeiraVez = tino.preparar(usuario);
            List<Resposta> respostas = switch (tipoDaDm(texto)) {
                case LANCAMENTO -> lote(usuario, texto);
                case CONSULTA -> List.of(responderConsulta(usuario, Entrada.consulta(texto)));
                // Na primeira vez, as boas-vindas já explicam como escrever.
                case NAO_ENTENDI -> primeiraVez ? List.of() : List.of(Resposta.de(Visual.naoEntendi()));
            };
            if (primeiraVez) {
                evento.getMessage().replyEmbeds(Visual.boasVindas()).queue();
            }
            for (Resposta resposta : respostas) {
                var acao = evento.getMessage().replyEmbeds(resposta.embed());
                if (resposta.botoes() != null) {
                    acao = acao.setComponents(resposta.botoes());
                }
                acao.queue();
            }
        } catch (IllegalArgumentException e) {
            evento.getMessage().replyEmbeds(Visual.aviso(e.getMessage())).queue();
        } catch (Exception e) {
            String codigo = codigoDeErro();
            LOG.error("falha ao interpretar mensagem [{}]", codigo, e);
            evento.getMessage().replyEmbeds(Visual.erroInterno(codigo)).queue();
        }
    }

    enum Dm { LANCAMENTO, CONSULTA, NAO_ENTENDI }

    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Random SORTEIO = new Random();

    /** Código curto que vai na mensagem e no log: a pessoa reporta, o mantenedor acha. */
    static String codigoDeErro() {
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            sb.append(ALFABETO.charAt(SORTEIO.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }

    /** Na DM a conversa é só com o Tino: o que não é lançamento nem consulta recebe instrução. */
    static Dm tipoDaDm(String texto) {
        if (Entrada.pareceLancamento(texto)) {
            return Dm.LANCAMENTO;
        }
        return Entrada.consulta(texto) == null ? Dm.NAO_ENTENDI : Dm.CONSULTA;
    }

    /** "quanto gastei essa semana", "extrato de hoje" e afins. */
    private Resposta responderConsulta(String usuario, Entrada.Consulta consulta)
            throws SQLException {
        return switch (consulta.tipo()) {
            case "lancamentos" -> pagina(usuario, consulta.periodo(), 0);
            case "categorias" -> Resposta.de(Visual.categorias(tino.categorias(usuario)));
            case "fixos" -> Resposta.de(Visual.recorrentes(tino.fixos(usuario)));
            case "ajuda" -> Resposta.de(Visual.ajuda());
            default -> Resposta.de(resumo(usuario, consulta.periodo()));
        };
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent evento) {
        String[] partes = evento.getComponentId().split(":");
        try {
            // O botão carrega a conta do dono; quem clica precisa resolver para a mesma.
            if (partes.length < 3 || !partes[1].equals(tino.conta("discord", evento.getUser().getId()))) {
                evento.reply("Esse botão não é seu.").setEphemeral(true).queue();
                return;
            }
            switch (partes[0]) {
                case "desfazer" -> {
                    boolean removido = tino.remover(partes[1], Long.parseLong(partes[2]));
                    evento.editMessageEmbeds(removido
                                    ? Visual.ok("Lançamento `#%s` desfeito.".formatted(partes[2]))
                                    : Visual.aviso("Esse lançamento já não existe."))
                            .setComponents()
                            .queue();
                }
                case "editar" -> {
                    Optional<Lancamento> alvo = tino.buscar(partes[1], Long.parseLong(partes[2]));
                    if (alvo.isEmpty()) {
                        evento.reply("Esse lançamento já não existe.").setEphemeral(true).queue();
                        return;
                    }
                    evento.replyModal(Visual.edicao(partes[1], alvo.get())).queue();
                }
                case "confirmar" -> {
                    Optional<Tino.Registro> r = tino.confirmar(partes[1], partes[2]);
                    if (r.isEmpty()) {
                        jaRespondido(evento);
                    } else {
                        evento.editMessageEmbeds(Visual.registro(r.get()))
                                .setComponents(Visual.acoesDoRegistro(partes[1], r.get().id())).queue();
                    }
                }
                case "corrigir" -> {
                    if (!tino.descartar(partes[1], partes[2])) {
                        jaRespondido(evento);
                    } else {
                        evento.editMessageEmbeds(Visual.ok("Não anotei. Manda de novo com o valor certo."))
                                .setComponents().queue();
                    }
                }
                case "pag" -> {
                    Resposta r = pagina(partes[1], Escala.de(partes[2]), Integer.parseInt(partes[3]));
                    evento.editMessageEmbeds(r.embed()).setComponents(r.botoes()).queue();
                }
                default -> evento.deferEdit().queue();
            }
        } catch (IllegalArgumentException e) {
            evento.replyEmbeds(Visual.aviso(e.getMessage())).setEphemeral(true).queue();
        } catch (Exception e) {
            String codigo = codigoDeErro();
            LOG.error("falha no botão {} [{}]", partes[0], codigo, e);
            evento.replyEmbeds(Visual.erroInterno(codigo)).setEphemeral(true).queue();
        }
    }

    /** Clique duplo ou expirado: não mexe na mensagem, que pode já mostrar o registro. */
    private static void jaRespondido(ButtonInteractionEvent evento) {
        evento.reply("Esse aviso já foi respondido ou expirou.").setEphemeral(true).queue();
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent evento) {
        String[] partes = evento.getModalId().split(":");
        evento.deferEdit().queue();
        try {
            if (partes.length < 3 || !partes[1].equals(tino.conta("discord", evento.getUser().getId()))) {
                evento.getHook().sendMessage("Esse formulário não é seu.").setEphemeral(true).queue();
                return;
            }
            long id = Long.parseLong(partes[2]);
            String usuario = partes[1];

            // O formulário mostra o valor sem sinal; o sinal vem do lançamento original.
            Lancamento antes = tino.buscar(usuario, id).orElseThrow(
                    () -> new IllegalArgumentException("esse lançamento já não existe"));
            long centavos = Dinheiro.paraCentavos(campo(evento, "valor"));
            Pedido pedido = new Pedido(antes.centavos() > 0 ? centavos : -centavos,
                    campo(evento, "categoria"), campo(evento, "descricao"),
                    Entrada.data(campo(evento, "data"), tino.hoje()));

            Tino.Registro r = tino.editar(usuario, id, pedido);
            evento.getHook().editOriginalEmbeds(Visual.registro(r)).queue();
        } catch (IllegalArgumentException e) {
            evento.getHook().sendMessageEmbeds(Visual.aviso(e.getMessage())).setEphemeral(true).queue();
        } catch (Exception e) {
            String codigo = codigoDeErro();
            LOG.error("falha ao editar [{}]", codigo, e);
            evento.getHook().sendMessageEmbeds(Visual.erroInterno(codigo)).setEphemeral(true).queue();
        }
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent evento) {
        if (!evento.getFocusedOption().getName().equals("categoria")) {
            return;
        }
        try {
            String usuario = tino.conta("discord", evento.getUser().getId());
            tino.preparar(usuario);
            List<String> nomes = tino.caminhos(usuario,
                    tipoDoContexto(evento), evento.getFocusedOption().getValue(), 25);
            evento.replyChoiceStrings(nomes).queue();
        } catch (SQLException e) {
            evento.replyChoiceStrings().queue();
        }
    }

    /** Em /gasto e /ganho o tipo vem do comando; em /categoria, da opção escolhida. */
    private static Tipo tipoDoContexto(CommandAutoCompleteInteractionEvent evento) {
        return switch (evento.getName()) {
            case "gasto" -> Tipo.GASTO;
            case "ganho" -> Tipo.GANHO;
            case "fixo" -> null;   // fixo aceita os dois
            default -> {
                OptionMapping tipo = evento.getOption("tipo");
                yield tipo == null ? null : Tipo.de(tipo.getAsString());
            }
        };
    }

    // -------------------------------------------------------------- comandos

    private Resposta registrar(SlashCommandInteractionEvent evento, String usuario, boolean ganho)
            throws SQLException {
        long valor = Dinheiro.paraCentavos(evento.getOption("valor").getAsString());
        LocalDate data = Entrada.data(texto(evento, "data"), tino.hoje());
        return resposta(usuario, tino.lancar(usuario, new Pedido(ganho ? valor : -valor,
                texto(evento, "categoria"), texto(evento, "descricao"), data)));
    }

    private Resposta resposta(String usuario, Evento evento) {
        return switch (evento) {
            case Evento.Registrado(Tino.Registro r) -> registro(usuario, r);
            case Evento.ForaDoNormal f -> perguntar(usuario, f);
        };
    }

    private Resposta perguntar(String usuario, Evento.ForaDoNormal f) {
        return new Resposta(Visual.foraDoNormal(f), Visual.confirmacao(usuario, f.chave()));
    }

    private static Resposta registro(String usuario, Tino.Registro r) {
        return new Resposta(Visual.registro(r), Visual.acoesDoRegistro(usuario, r.id()));
    }

    /** Vários lançamentos numa tacada, tanto por /lancar quanto por texto solto na DM. */
    private List<Resposta> lote(String usuario, String texto) throws SQLException {
        List<Tino.Registro> registrados = new ArrayList<>();
        List<Resposta> perguntas = new ArrayList<>();
        for (Evento e : tino.lancar(usuario, texto)) {
            switch (e) {
                case Evento.Registrado(Tino.Registro r) -> registrados.add(r);
                case Evento.ForaDoNormal f -> perguntas.add(perguntar(usuario, f));
            }
        }
        List<Resposta> respostas = new ArrayList<>();
        if (registrados.size() == 1) {
            respostas.add(registro(usuario, registrados.getFirst()));
        } else if (registrados.size() > 1) {
            respostas.add(Resposta.de(Visual.lote(registrados)));
        }
        respostas.addAll(perguntas);
        return respostas;
    }

    private MessageEmbed resumo(String usuario, Escala escala) throws SQLException {
        Tino.Comparativo c = tino.resumo(usuario, escala);
        return Visual.resumo(c.periodo(), c.atual(), c.anterior(), c.ritmo());
    }

    /** Uma página de lançamentos com os botões de navegação, quando fazem sentido. */
    private Resposta pagina(String usuario, Escala escala, int offset) throws SQLException {
        Tino.Pagina p = tino.pagina(usuario, escala, offset);
        MessageEmbed embed = Visual.lancamentos(p.periodo(), p.itens(), offset, p.temProxima());
        return offset == 0 && !p.temProxima()
                ? Resposta.de(embed)
                : new Resposta(embed, Visual.paginacao(usuario, escala.codigo(), offset,
                        p.temProxima(), Tino.LIMITE_PAGINA));
    }

    private void exportar(SlashCommandInteractionEvent evento, String usuario) throws SQLException {
        Tino.Exportacao x = tino.exportar(usuario, periodo(evento));
        if (x.quantidade() == 0) {
            evento.getHook().sendMessageEmbeds(Visual.aviso("Nada para exportar neste período.")).queue();
            return;
        }
        evento.getHook()
                .sendMessageEmbeds(Visual.ok("%d lançamentos de %s a %s.".formatted(
                        x.quantidade(), x.periodo().inicio(), x.periodo().fim())))
                .addFiles(FileUpload.fromData(x.csv().getBytes(StandardCharsets.UTF_8),
                        "lancamentos-%s.csv".formatted(x.periodo().inicio())))
                .queue();
    }

    private MessageEmbed remover(SlashCommandInteractionEvent evento, String usuario)
            throws SQLException {
        long id = evento.getOption("id").getAsLong();
        return tino.remover(usuario, id)
                ? Visual.ok("Lançamento `#%d` removido.".formatted(id))
                : Visual.aviso("Não achei o lançamento `#%d`.".formatted(id));
    }

    private MessageEmbed categoria(SlashCommandInteractionEvent evento, String usuario)
            throws SQLException {
        String opcao = texto(evento, "tipo");   // "listar" não tem tipo
        Tipo tipo = opcao == null ? null : Tipo.de(opcao);
        return switch (evento.getSubcommandName()) {
            case "criar" -> {
                String nome = evento.getOption("nome").getAsString();
                tino.criarCategoria(usuario, nome, tipo);
                yield Visual.ok("Categoria de %s criada: **%s**".formatted(tipo.name().toLowerCase(), nome));
            }
            case "renomear" -> {
                String antes = evento.getOption("categoria").getAsString();
                String depois = evento.getOption("nome").getAsString();
                tino.renomearCategoria(usuario, antes, depois, tipo);
                yield Visual.ok("**%s** agora é **%s**.".formatted(antes, depois));
            }
            case "excluir" -> {
                String alvo = evento.getOption("categoria").getAsString();
                tino.excluirCategoria(usuario, alvo, tipo);
                yield Visual.ok("**%s** excluída.".formatted(alvo));
            }
            default -> Visual.categorias(tino.categorias(usuario));
        };
    }

    private MessageEmbed fixo(SlashCommandInteractionEvent evento, String usuario)
            throws SQLException {
        return switch (evento.getSubcommandName()) {
            case "adicionar" -> {
                Entrada.Item item = Entrada.item(evento.getOption("valor").getAsString());
                String categoria = evento.getOption("categoria").getAsString();
                tino.adicionarFixo(usuario, item.ganho() ? item.centavos() : -item.centavos(),
                        categoria, texto(evento, "descricao"));
                yield Visual.ok("Fixo cadastrado: **%s** em %s".formatted(
                        Dinheiro.formatar(item.centavos()), categoria));
            }
            case "remover" -> {
                long id = evento.getOption("id").getAsLong();
                yield tino.removerFixo(usuario, id)
                        ? Visual.ok("Fixo `#%d` removido.".formatted(id))
                        : Visual.aviso("Não achei o fixo `#%d`.".formatted(id));
            }
            case "lancar" -> {
                int quantos = tino.lancarFixos(usuario);
                yield quantos == 0
                        ? Visual.aviso("Nenhum fixo cadastrado. Use `/fixo adicionar`.")
                        : Visual.ok("%d fixo(s) lançados hoje. Confira em `/lancamentos`."
                                .formatted(quantos));
            }
            default -> Visual.recorrentes(tino.fixos(usuario));
        };
    }

    // ---------------------------------------------------------------- apoio

    /** Embed e, quando houver, a linha de botões que o acompanha. */
    private record Resposta(MessageEmbed embed, ActionRow botoes) {
        static Resposta de(MessageEmbed embed) {
            return new Resposta(embed, null);
        }
    }

    private static Escala periodo(SlashCommandInteractionEvent evento) {
        OptionMapping opcao = evento.getOption("periodo");
        return opcao == null ? Escala.MES : Escala.de(opcao.getAsString());
    }

    private static String texto(SlashCommandInteractionEvent evento, String nome) {
        OptionMapping opcao = evento.getOption(nome);
        return opcao == null ? null : opcao.getAsString();
    }

    private static String campo(ModalInteractionEvent evento, String nome) {
        var valor = evento.getValue(nome);
        return valor == null ? null : valor.getAsString();
    }
}
