-- Quando a conta nasceu e quando cada lançamento foi escrito: lancamento.data é o dia do gasto.
-- As linhas que já existem ficam com a hora desta migration.
ALTER TABLE conta ADD COLUMN criado_em TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE lancamento ADD COLUMN criado_em TIMESTAMPTZ NOT NULL DEFAULT now();
