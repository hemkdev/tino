# Política de Privacidade — Tino

**Última atualização:** 8 de novembro de 2026

O Tino é um bot de Discord de código aberto, mantido por
[hemkdev](https://github.com/hemkdev) como projeto pessoal. Esta política
descreve, sem rodeios, o que ele guarda e o que faz com isso.

## O que é armazenado

Quando você registra um lançamento, ficam guardados:

- **Seu identificador de usuário do Discord** — o número da sua conta no
  Discord, ligado a uma conta interna do Tino (um número sequencial). É essa
  conta interna que separa seus dados dos de outras pessoas e que, no futuro,
  permitirá entrar por outros canais vendo os mesmos lançamentos
- **Valor, categoria e data** de cada gasto ou ganho
- **Descrição**, quando você escreve uma
- **Suas categorias**, incluindo as que você criar ou renomear
- **Lançamentos fixos** que você cadastrar
- **Lançamentos à espera de confirmação** — quando um valor foge muito do seu
  normal, o pedido fica guardado até você responder [Tá certo] ou [Corrigir],
  por no máximo 24 horas, e depois é apagado

Nada além disso.

## O que não é armazenado

- Nome de usuário, apelido, e-mail, avatar ou qualquer outro dado de perfil
- Mensagens que não sejam lançamentos ou consultas ao bot
- Qualquer coisa dita em servidores dos quais o bot participe
- Dados bancários, número de cartão ou credenciais — o Tino não se conecta a
  nenhuma instituição financeira e não tem como fazê-lo

## Onde os dados ficam

Num banco Postgres, numa máquina virtual na Oracle Cloud operada só pelo
mantenedor do projeto. Não há banco gerenciado: o Postgres roda na própria
máquina.

Uma cópia de segurança do banco é feita todo dia e antes de cada atualização do
bot. Ela é criptografada antes de sair da máquina e guardada no Cloudflare R2 (o
armazenamento da Cloudflare). A chave que abre essas cópias fica só com o
mantenedor, fora da máquina e fora da Cloudflare: quem guarda o arquivo não
consegue ler o conteúdo. As cópias diárias são apagadas depois de 7 dias, e as
de antes de uma atualização, depois de 30.

A Oracle, que fornece a máquina, e a Cloudflare, que guarda as cópias, não têm
nenhum papel no uso dos dados. O serviço que avisa quando o bot cai
(healthchecks.io) recebe só um sinal periódico de que ele está vivo, sem dado
nenhum.

Isso tem uma consequência que você deve conhecer antes de usar: quem opera a
máquina tem acesso técnico a esse banco. O bot não expõe os dados de uma
pessoa para outra, e não há intenção nem prática de leitura desse conteúdo — mas
seria desonesto afirmar que isso é tecnicamente impossível. Se essa condição não
te agrada, você pode rodar sua própria instância: o código está inteiro no
repositório e as instruções estão no README.

## Quem consegue ver

Somente você. Toda consulta do bot é filtrada pela sua conta,
e existem testes automatizados que verificam esse isolamento.

Em conversa privada, as respostas ficam no seu histórico do Discord. Em
servidores, as respostas são *ephemeral* — visíveis apenas para quem executou o
comando.

Lembre-se de que as mensagens trocadas com qualquer bot passam pela
infraestrutura do Discord e ficam sujeitas à
[política de privacidade deles](https://discord.com/privacy), que está fora do
controle deste projeto.

## Compartilhamento

Seus dados não são vendidos, cedidos, compartilhados nem usados para publicidade,
treinamento de modelos ou qualquer finalidade além de responder aos seus próprios
comandos.

## Por quanto tempo

Enquanto você quiser. Não há expiração automática.

Você pode apagar dados a qualquer momento:

- **Um lançamento** — botão *Desfazer* logo após registrar, ou `/remover <id>`
- **Uma categoria** — `/categoria excluir` (recusa se houver lançamentos, para
  não apagar histórico sem querer)
- **Tudo** — abra uma
  [issue no repositório](https://github.com/hemkdev/Tino/issues)
  informando seu identificador de usuário do Discord. A remoção é feita
  manualmente e sem questionamento

Ao remover o Tino da sua conta, os dados permanecem guardados, caso você volte.
Querendo apagá-los, faça o pedido acima.

Um dado apagado ainda pode existir nas cópias de segurança criptografadas por até
30 dias, até elas expirarem.

## Segurança

O que existe: isolamento por usuário em todas as consultas, respostas privadas,
e nenhum valor monetário ou descrição gravado em logs.

O que não existe, dito com clareza: o banco na máquina não é criptografado (só
as cópias de segurança são), e a infraestrutura é uma máquina operada por uma
pessoa, não um ambiente com garantias de disponibilidade ou auditoria. É um projeto pessoal gratuito, e o nível de
proteção é compatível com isso. Não confie a ele informação que você não possa
perder ou que não gostaria que existisse fora do seu controle.

## Menores de idade

O Discord exige idade mínima para uso da plataforma. Este bot segue os
[Termos de Serviço do Discord](https://discord.com/terms) e não se destina a quem
não tem idade para usá-lo.

## Mudanças nesta política

Alterações são publicadas neste arquivo, com a data acima atualizada. O histórico
completo de versões fica visível no
[repositório](https://github.com/hemkdev/Tino/commits/main/docs/PRIVACIDADE.md).

## Contato

Dúvidas, pedidos de exclusão ou problemas:
[issues do repositório](https://github.com/hemkdev/Tino/issues).
