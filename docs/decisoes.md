# Decisões

O que foi escolhido, o que foi recusado e por quê. O esquema e as regras do banco estão em
[banco.md](banco.md); as do personagem, em [personagem.md](personagem.md).

## 1. Sem framework e sem ORM

Java 25, JDA, o driver JDBC do Postgres, Flyway e JUnit. O projeto começou com arquitetura hexagonal completa, com
portas, adaptadores e documento de especificação, e foi reduzido de propósito quando ficou claro
que a estrutura era maior que o problema. SQL escrito à mão cabe numa classe (`Financas`) e não
esconde o que cada consulta faz. Os commits dessa fase continuam no histórico.

## 2. O núcleo não conhece o canal

```
tino/nucleo/    Tino (casos de uso), Financas (SQL), Entrada, Dinheiro e os records do domínio
tino/discord/   Bot (eventos do JDA), Visual (embeds e botões), Estado
```

Canal novo (site, Telegram, MCP) fala só com a classe `Tino`. `Financas` é package-private, então
a fronteira não depende de disciplina: chamar SQL de fora de `tino.nucleo` não compila. Cálculo
(projeção do mês, CSV, interpretação de data) e regra (fora do normal, pendentes, madrugada)
moram no núcleo; o canal só traduz a entrada e formata a saída. O teste: se um segundo canal
precisaria copiar, é núcleo.

## 3. Um processo, uma conexão, um lock

Discord, site e MCP rodam no mesmo processo e compartilham a instância de `Tino`. Ela tem uma
conexão só, e os métodos são `synchronized`. Um app pessoal não tem concorrência que justifique
pool; se tiver, a troca é por HikariCP sem mexer na API.

## 4. Conta própria, várias identidades

Os dados são da conta do Tino, não do id do Discord. Cada canal resolve a conta com
`Tino.conta(provedor, externo)` na entrada, e entrar pelo Google mais tarde é ligar mais uma
identidade à mesma conta.

## 5. Evento selado em vez de flag

`Tino.lancar` devolve um `Evento`: `Registrado` ou `ForaDoNormal`. Quando o valor passa de 5× a
média recente da categoria (90 dias até a data do lançamento), nada é gravado e o canal pergunta
antes; a resposta volta por `Tino.confirmar` ou `Tino.descartar`. Não há outro jeito de gravar:
`registrar` é package-private. Por ser `sealed`, o `switch` de cada canal precisa cobrir todos os casos para
compilar, então um evento novo não passa despercebido em nenhum canal. Erro de digitação continua
como `IllegalArgumentException`, que é o idioma do Java para entrada inválida.

O pedido à espera de confirmação fica na tabela `pendente` ([banco.md](banco.md)), com chave
aleatória, retirada só pela dona e validade de 24 h. Sobrevive a reinício, e qualquer canal
responde por ele com a chave que recebeu no `ForaDoNormal`.

## 6. O relógio é do núcleo

`Tino` recebe um `java.time.Clock` no fuso America/Sao_Paulo (`Clock.fixed` nos testes). Data
futura, madrugada, mês corrente e expiração de pendente usam o mesmo "agora", e nenhum canal
calcula a data: quem precisa interpretar uma data digitada usa `tino.hoje()`. As classes puras
(`Entrada`, `Periodo`, `Financas`) continuam recebendo `LocalDate hoje` por parâmetro. Fuso por
pessoa entra com o site, que sabe o fuso do navegador; o Discord não sabe.

## 7. Sem interface de repositório

Existe uma implementação só, e os testes rodam contra um Postgres 17 de verdade (o do
`docker-compose.yml`, num banco `tino_teste`; no CI, um service), o mesmo de produção. Cada teste
começa com `TRUNCATE ... RESTART IDENTITY CASCADE`. Uma interface aqui só existiria para ser
trocada por mock, e não há o que trocar.

Antes, os testes rodavam contra SQLite em memória. Com o Postgres, o banco do teste é o de
produção: diferença de SQL entre os dois (o `LIKE` que ignora caixa num e não no outro) aparece
no teste, e não no ar.

## 8. Personagem próprio

O mascote antigo derivava do Wumpus. As diretrizes de marca da Discord proíbem personagens
parecidos, e um mascote da Discord não serve num produto que vai para Telegram e site. O Tino é
uma criatura inventada, desenhada em SVG e animada só com CSS, para sair como SVG, GIF ou PNG sem
JavaScript. Detalhes em [personagem.md](personagem.md).

## 9. Postgres com migrations, e migrar não é subir

O SQLite criava o esquema no boot e convertia dados antigos ali mesmo. Antes do primeiro deploy
(e antes de ter usuário real, quando a troca é mais barata), o banco virou Postgres 17 em
container, com migrations versionadas pelo Flyway. Só para frente: o Flyway Community não tem
*undo*, e a rede de segurança é o `pg_dump` antes de migrar e as migrations testadas no CI.

Migrar é um passo próprio (`java -jar tino.jar migrar`); o boot só roda o `validate`. No deploy,
uma migration que falha para ali, e o bot antigo continua no ar.

Continua uma conexão só (§3). Quando ela cai (SQLState classe `08`, ou `isValid` falso), o bot
avisa a pessoa que está instável, sai com código 1 e o Docker religa com uma conexão nova. Os
outros erros seguem no "Algo deu errado" com código. Reconexão interna seria código a manter
para um caso que o restart já resolve.

## Recusado

| Tentativa | Por que saiu |
|---|---|
| Arquitetura hexagonal com portas e adaptadores | estrutura maior que o problema |
| Interface de repositório | uma implementação só, testes já rápidos |
| SQLite em produção | esquema no boot, um escritor por vez, backup como arquivo solto |
| Rollback automático de migration | o Flyway Community não tem *undo*; correção vai numa migration nova |
| Gravar e avisar valor fora do normal | o erro de digitação já virava histórico; agora pergunta antes |
| Chave sequencial para o pedido pendente | depois de reiniciar, um botão antigo acertava o pedido de outra pessoa |
| Mascote derivado do Wumpus | diretrizes de marca da Discord |
| Fusão de duas contas com dados | sem regra clara de qual dado vence; vincular a uma conta já usada é recusado |
