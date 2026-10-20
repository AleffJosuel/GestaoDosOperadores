/**
 * Datas do dia a dia, sempre no fuso LOCAL do navegador.
 *
 * Não use `new Date().toISOString().slice(0, 10)`: o ISO é em UTC, então no Brasil
 * (UTC−3) a partir das 21h ele já devolve o dia seguinte. Isso fazia o front mandar
 * uma data futura para campos validados com `@PastOrPresent` no backend (a avaliação
 * e o registro de acompanhamento falhavam à noite) e divergia do `LocalDate.now()`
 * usado para contar ações de PDI atrasadas.
 */

/** Data de hoje no fuso local, no formato AAAA-MM-DD (o mesmo que o <input type="date"> usa). */
export function hoje(): string {
  return emIso(new Date());
}

/** Converte um Date para AAAA-MM-DD usando os componentes locais, não os de UTC. */
export function emIso(d: Date): string {
  const mes = String(d.getMonth() + 1).padStart(2, "0");
  const dia = String(d.getDate()).padStart(2, "0");
  return `${d.getFullYear()}-${mes}-${dia}`;
}
