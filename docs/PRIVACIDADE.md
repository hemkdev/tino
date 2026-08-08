# Política de Privacidade — Tino

**Última atualização:** 8 de agosto de 2026

O Tino é um bot de Discord de código aberto, mantido por
[hemkdev](https://github.com/hemkdev) como projeto pessoal. Esta política
descreve, sem rodeios, o que ele guarda e o que faz com isso.

## O que é armazenado

Quando você registra um lançamento, ficam guardados:

- **Seu identificador de usuário do Discord** — o número da sua conta, usado
  para separar seus dados dos de outras pessoas
- **Valor, categoria e data** de cada gasto ou ganho
- **Descrição**, quando você escreve uma
- **Suas categorias**, incluindo as que você criar ou renomear
- **Lançamentos fixos** que você cadastrar

Nada além disso.

## O que não é armazenado

- Nome de usuário, apelido, e-mail, avatar ou qualquer outro dado de perfil
- Mensagens que não sejam lançamentos ou consultas ao bot
- Qualquer coisa dita em servidores dos quais o bot participe
- Dados bancários, número de cartão ou credenciais — o Tino não se conecta a
  nenhuma instituição financeira e não tem como fazê-lo

## Onde os dados ficam

Num arquivo SQLite, em uma máquina operada pelo mantenedor do projeto. **Não há
serviço em nuvem, banco gerenciado nem terceiros envolvidos.**

Isso tem uma consequência que você deve conhecer antes de usar: quem opera a
máquina tem acesso técnico a esse arquivo. O bot não expõe os dados de uma
pessoa para outra, e não há intenção nem prática de leitura desse conteúdo — mas
seria desonesto afirmar que isso é tecnicamente impossível. Se essa condição não
te agrada, você pode rodar sua própria instância: o código está inteiro no
repositório e as instruções estão no README.

## Quem consegue ver

Somente você. Toda consulta do bot é filtrada pelo seu identificador de usuário,
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
  [issue no repositório](https://github.com/hemkdev/finance-bot/issues)
  informando seu identificador de usuário do Discord. A remoção é feita
  manualmente e sem questionamento

Ao remover o Tino da sua conta, os dados permanecem guardados, caso você volte.
Querendo apagá-los, faça o pedido acima.

## Segurança

O que existe: isolamento por usuário em todas as consultas, respostas privadas,
e nenhum valor monetário ou descrição gravado em logs.

O que não existe, dito com clareza: o arquivo de dados não é criptografado, e a
infraestrutura é uma máquina pessoal, não um ambiente com garantias de
disponibilidade ou auditoria. É um projeto pessoal gratuito, e o nível de
proteção é compatível com isso. Não confie a ele informação que você não possa
perder ou que não gostaria que existisse fora do seu controle.

## Menores de idade

O Discord exige idade mínima para uso da plataforma. Este bot segue os
[Termos de Serviço do Discord](https://discord.com/terms) e não se destina a quem
não tem idade para usá-lo.

## Mudanças nesta política

Alterações são publicadas neste arquivo, com a data acima atualizada. O histórico
completo de versões fica visível no
[repositório](https://github.com/hemkdev/finance-bot/commits/main/docs/PRIVACIDADE.md).

## Contato

Dúvidas, pedidos de exclusão ou problemas:
[issues do repositório](https://github.com/hemkdev/finance-bot/issues).
