CREATE TABLE supervisor (
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    senha_hash  VARCHAR(100) NOT NULL,
    criado_em   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE colaborador (
    id             BIGSERIAL PRIMARY KEY,
    supervisor_id  BIGINT NOT NULL REFERENCES supervisor(id),
    matricula      VARCHAR(30)  NOT NULL,
    nome           VARCHAR(150) NOT NULL,
    cargo          VARCHAR(80)  NOT NULL,
    setor          VARCHAR(80)  NOT NULL,
    data_admissao  DATE,
    escolaridade   VARCHAR(60),
    ativo          BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (supervisor_id, matricula)
);

CREATE TABLE competencia (
    id              BIGSERIAL PRIMARY KEY,
    supervisor_id   BIGINT NOT NULL REFERENCES supervisor(id),
    nome            VARCHAR(120) NOT NULL,
    tipo            VARCHAR(20)  NOT NULL CHECK (tipo IN ('COMPORTAMENTAL','TECNICA')),
    descricao       TEXT,
    criterio        TEXT,
    nivel_esperado  SMALLINT NOT NULL DEFAULT 5 CHECK (nivel_esperado BETWEEN 1 AND 5),
    acao_padrao     VARCHAR(200),
    ativa           BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uq_competencia_nome ON competencia (supervisor_id, lower(nome));

CREATE TABLE rotulo_nota (
    supervisor_id  BIGINT   NOT NULL REFERENCES supervisor(id),
    nota           SMALLINT NOT NULL CHECK (nota BETWEEN 1 AND 5),
    rotulo         VARCHAR(40) NOT NULL,
    PRIMARY KEY (supervisor_id, nota)
);

CREATE TABLE configuracao_classificacao (
    supervisor_id       BIGINT PRIMARY KEY REFERENCES supervisor(id),
    limite_critico      NUMERIC(3,2) NOT NULL DEFAULT 2.00,
    limite_desenvolver  NUMERIC(3,2) NOT NULL DEFAULT 3.00,
    limite_bom          NUMERIC(3,2) NOT NULL DEFAULT 4.00,
    CHECK (limite_critico < limite_desenvolver AND limite_desenvolver < limite_bom)
);

CREATE TABLE avaliacao (
    id              BIGSERIAL PRIMARY KEY,
    colaborador_id  BIGINT NOT NULL REFERENCES colaborador(id),
    data_avaliacao  DATE   NOT NULL,
    observacao      TEXT,
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (colaborador_id, data_avaliacao)
);

CREATE TABLE nota_competencia (
    avaliacao_id    BIGINT   NOT NULL REFERENCES avaliacao(id) ON DELETE CASCADE,
    competencia_id  BIGINT   NOT NULL REFERENCES competencia(id),
    nota            SMALLINT NOT NULL CHECK (nota BETWEEN 1 AND 5),
    PRIMARY KEY (avaliacao_id, competencia_id)
);

CREATE TABLE acao_pdi (
    id                     BIGSERIAL PRIMARY KEY,
    colaborador_id         BIGINT NOT NULL REFERENCES colaborador(id),
    competencia_id         BIGINT NOT NULL REFERENCES competencia(id),
    avaliacao_origem_id    BIGINT NOT NULL REFERENCES avaliacao(id),
    descricao              TEXT   NOT NULL,
    responsavel            VARCHAR(150),
    funcao_area            VARCHAR(80),
    carga_horaria_minutos  INTEGER CHECK (carga_horaria_minutos > 0),
    prazo                  DATE,
    status                 VARCHAR(20) NOT NULL DEFAULT 'NAO_INICIADO'
                           CHECK (status IN ('NAO_INICIADO','PLANEJADO','EM_ANDAMENTO','CONCLUIDO')),
    data_conclusao         DATE,
    evidencia              TEXT,
    observacao             TEXT,
    criado_em              TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (status <> 'CONCLUIDO' OR data_conclusao IS NOT NULL)
);

CREATE TABLE registro_acompanhamento (
    id              BIGSERIAL PRIMARY KEY,
    colaborador_id  BIGINT NOT NULL REFERENCES colaborador(id),
    tipo            VARCHAR(20) NOT NULL CHECK (tipo IN ('FEEDBACK','CONVERSA','OCORRENCIA','OUTRO')),
    data_evento     DATE NOT NULL,
    descricao       TEXT NOT NULL,
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_colaborador_supervisor_ativo ON colaborador (supervisor_id, ativo);
CREATE INDEX idx_avaliacao_colaborador_data   ON avaliacao (colaborador_id, data_avaliacao DESC);
CREATE INDEX idx_acao_pdi_colaborador_status  ON acao_pdi (colaborador_id, status);
CREATE INDEX idx_acao_pdi_prazo_abertas       ON acao_pdi (prazo) WHERE status <> 'CONCLUIDO';
CREATE INDEX idx_registro_colaborador_data    ON registro_acompanhamento (colaborador_id, data_evento DESC);
