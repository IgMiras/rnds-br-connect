package br.rndsconnect.builder;

import org.hl7.fhir.r4.model.Bundle;

/**
 * Contrato 5 da arquitetura — construção do Bundle (Camada 3).
 *
 * <p>Um Builder por documento, sem herança entre eles (ADR-06). Os perfis dos sete documentos
 * diferem em resources, extensões obrigatórias e cardinalidade; herança criaria
 * acoplamento estrutural e dificultaria validar cada perfil de forma independente.
 *
 * @param <T> o DTO enriquecido do serviço
 * @author Igor Mendes Domingues Miras
 */
public interface FhirBundleBuilder<T> {

    /**
     * Monta o Bundle FHIR R4 a partir de dados já traduzidos para o vocabulário da RNDS.
     *
     * @param enrichedDto DTO enriquecido produzido pela Camada 2
     * @return Bundle pronto para a validação da Camada 4
     * @throws IllegalArgumentException se faltar campo que o perfil exige estruturalmente
     */
    Bundle build(T enrichedDto);
}
