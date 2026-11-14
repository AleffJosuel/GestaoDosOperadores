-- Trilha de auditoria: quem alterou o quê, quando e de onde.
--
-- Guarda apenas metadados da requisição (método, caminho, status, IP, duração). O corpo NUNCA é
-- gravado: ele carregaria avaliação, PDI e, no login, a senha.
--
-- O supervisor é identificado pelo e-mail, sem chave estrangeira, de propósito: a trilha precisa
-- continuar legível depois que a conta for removida — uma auditoria que desaparece junto com o
-- autor não serve de auditoria.
CREATE TABLE auditoria (
    id            BIGSERIAL PRIMARY KEY,
    ocorrido_em   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    autor_email   VARCHAR(255),
    metodo        VARCHAR(10)  NOT NULL,
    caminho       VARCHAR(255) NOT NULL,
    status        SMALLINT     NOT NULL,
    ip            VARCHAR(45),
    duracao_ms    INTEGER      NOT NULL
);

CREATE INDEX idx_auditoria_ocorrido_em ON auditoria (ocorrido_em DESC);
CREATE INDEX idx_auditoria_autor       ON auditoria (autor_email, ocorrido_em DESC);
