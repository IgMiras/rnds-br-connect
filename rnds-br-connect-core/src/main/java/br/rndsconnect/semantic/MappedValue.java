package br.rndsconnect.semantic;

import br.rndsconnect.model.CodedTarget;

/**
 * Resultado de uma tradução bem-sucedida: o alvo e a prova de como se chegou nele.
 *
 * <p>Os dois andam juntos por decisão de projeto (ADR-04). Devolver só o alvo tornaria a
 * proveniência opcional, e proveniência opcional é proveniência ausente.
 *
 * @param target o par ({@code system}, {@code code}) da RNDS
 * @param provenance trilha de auditoria da tradução
 * @author Igor Mendes Domingues Miras
 */
public record MappedValue(CodedTarget target, MappingProvenance provenance) {}
