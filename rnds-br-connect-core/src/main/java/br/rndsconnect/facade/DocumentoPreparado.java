package br.rndsconnect.facade;

import br.rndsconnect.semantic.MappingAuditLog;
import org.hl7.fhir.r4.model.Bundle;

/**
 * Um documento pronto para envio: o Bundle validado e a prova de como ele foi construído.
 *
 * <p>Os dois andam juntos por decisão de projeto (ADR-04). Um Bundle sem trilha é um Bundle
 * cujas escolhas terminológicas não podem ser justificadas depois — e "depois" em saúde
 * pública costuma ser anos, quando um estudo epidemiológico encontra uma anomalia e alguém
 * precisa decidir se é achado clínico ou erro de integração.
 *
 * @param bundle documento FHIR conforme aos perfis da RNDS
 * @param trilha proveniência de cada tradução terminológica realizada
 * @author Igor Mendes Domingues Miras
 */
public record DocumentoPreparado(Bundle bundle, MappingAuditLog trilha) {}
