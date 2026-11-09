CREATE TABLE conta (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY
);

CREATE TABLE identidade (
    provedor TEXT   NOT NULL,
    externo  TEXT   NOT NULL,
    conta_id BIGINT NOT NULL REFERENCES conta (id),
    PRIMARY KEY (provedor, externo)
);

CREATE TABLE categoria (
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario BIGINT NOT NULL REFERENCES conta (id),
    pai_id  BIGINT REFERENCES categoria (id),
    tipo    TEXT   NOT NULL CHECK (tipo IN ('gasto', 'ganho')),
    nome    TEXT   NOT NULL
);

CREATE TABLE lancamento (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario        BIGINT NOT NULL REFERENCES conta (id),
    valor_centavos BIGINT NOT NULL CHECK (valor_centavos <> 0),
    categoria_id   BIGINT NOT NULL REFERENCES categoria (id),
    descricao      TEXT,
    data           DATE   NOT NULL
);

CREATE INDEX idx_consulta ON lancamento (usuario, data);

CREATE TABLE recorrente (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario        BIGINT NOT NULL REFERENCES conta (id),
    valor_centavos BIGINT NOT NULL CHECK (valor_centavos <> 0),
    categoria_id   BIGINT NOT NULL REFERENCES categoria (id),
    descricao      TEXT
);

CREATE TABLE pendente (
    chave          TEXT        PRIMARY KEY,
    usuario        BIGINT      NOT NULL REFERENCES conta (id),
    valor_centavos BIGINT      NOT NULL CHECK (valor_centavos <> 0),
    categoria      TEXT,
    descricao      TEXT,
    data           DATE        NOT NULL,
    criado_em      TIMESTAMPTZ NOT NULL
);
