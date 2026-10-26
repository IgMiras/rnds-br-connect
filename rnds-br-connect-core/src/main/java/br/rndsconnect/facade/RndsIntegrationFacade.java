package br.rndsconnect.facade;

import br.rndsconnect.dto.RegistroAtendimentoClinicoDTO;
import br.rndsconnect.dto.RegistroDispensacaoMedicamentoDTO;
import br.rndsconnect.dto.RegistroExameLaboratorialDTO;
import br.rndsconnect.dto.RegistroImunizacaoCampanhaDTO;
import br.rndsconnect.dto.RegistroImunizacaoDTO;
import br.rndsconnect.dto.RegistroRegulacaoAssistencialDTO;
import br.rndsconnect.dto.RegistroSumarioAltaDTO;

/**
 * Camada 1 — a única superfície que o sistema cliente enxerga.
 *
 * <p>Recebe dados clínicos no vocabulário local e devolve um documento conforme aos perfis
 * da RNDS. Entre uma coisa e outra acontecem tradução terminológica (Camada 2), construção
 * FHIR (Camada 3) e validação de conformidade (Camada 4) — e o sistema cliente não precisa
 * saber que nada disso existe.
 *
 * <p><strong>Nenhuma assinatura desta interface menciona FHIR.</strong> Não há {@code
 * Bundle} no parâmetro, não há {@code CodeableConcept}, não há perfil. É a concretização do
 * ADR-01: o core não conhece o cliente, e o cliente não conhece o core.
 *
 * <p><strong>Escopo atual:</strong> preparação. O envio pela Camada 5 — mTLS, ICP-Brasil,
 * cache de token — é fase futura, e por isso a interface fala em <em>preparar</em>, não em
 * <em>enviar</em>. Quando a Camada 5 existir, um método {@code enviar} se soma a este sem
 * alterar o que já está aqui.
 *
 * @author Igor Mendes Domingues Miras
 */
public interface RndsIntegrationFacade {

    /**
     * Prepara um registro de imunização de rotina (serviço RIA-R).
     *
     * <p>Traduz, constrói e valida. Falha na primeira etapa que não puder ser concluída — em
     * milissegundos, sem tráfego de rede, com diagnóstico que aponta o campo exato.
     *
     * @param registro dados clínicos no vocabulário do sistema cliente
     * @return documento validado e trilha de auditoria
     * @throws br.rndsconnect.semantic.UnmappedTerminologyException se algum código local não
     *     tiver correspondência registrada — nunca se repassa o código local
     * @throws br.rndsconnect.validator.FhirConformanceException se o documento resultante não
     *     for conforme aos perfis da RNDS
     * @throws IllegalArgumentException se faltar campo que o perfil exige estruturalmente
     */
    DocumentoPreparado prepararImunizacaoRotina(RegistroImunizacaoDTO registro);

    /**
     * Prepara um registro de imunização em campanha (serviço RIA-C).
     *
     * <p>Sobrecarga, e não parâmetro de configuração: os dois serviços têm perfis
     * incompatíveis entre si — o que um exige, o outro proíbe. Um método único com um
     * {@code enum} de serviço aceitaria em compilação um registro de campanha com local de
     * aplicação, que a RNDS recusa. Aqui, o tipo do argumento é a garantia.
     *
     * @param registro dados clínicos no vocabulário do sistema cliente
     * @return documento validado e trilha de auditoria
     * @throws br.rndsconnect.semantic.UnmappedTerminologyException se algum código local não
     *     tiver correspondência registrada
     * @throws br.rndsconnect.validator.FhirConformanceException se o documento resultante não
     *     for conforme ao perfil do RIA-C
     * @throws IllegalArgumentException se faltar campo que o perfil exige estruturalmente —
     *     em campanha, isso inclui fabricante e lote, opcionais na rotina
     */
    DocumentoPreparado prepararImunizacaoCampanha(RegistroImunizacaoCampanhaDTO registro);

    /**
     * Prepara um laudo de resultado de exame laboratorial (serviço REL).
     *
     * <p>Primeiro serviço do escopo com cardinalidade variável: um laudo com {@code N} exames
     * produz {@code 2 + 2 × N} resources, e a trilha de auditoria discrimina cada tradução
     * pelo índice do exame.
     *
     * @param registro dados clínicos no vocabulário do sistema cliente
     * @return documento validado e trilha de auditoria
     * @throws br.rndsconnect.semantic.UnmappedTerminologyException se algum código local não
     *     tiver correspondência registrada
     * @throws br.rndsconnect.validator.FhirConformanceException se o documento resultante não
     *     for conforme ao perfil do REL
     * @throws IllegalArgumentException se faltar campo que o perfil exige estruturalmente —
     *     inclusive {@code metodo} e {@code valorReferencia}, exigidos como texto livre
     */
    DocumentoPreparado prepararExameLaboratorial(RegistroExameLaboratorialDTO registro);

    /**
     * Prepara uma solicitação de regulação assistencial (serviço RIRA).
     *
     * <p>Único dos serviços cujo documento descreve uma <strong>solicitação em trânsito por
     * uma máquina de estados</strong>, e não um fato consumado: {@code Composition.event} é
     * {@code 1..*}, com sete estados possíveis, e o mesmo documento carrega o histórico.
     *
     * @param registro dados clínicos no vocabulário do sistema cliente
     * @return documento validado e trilha de auditoria
     * @throws br.rndsconnect.semantic.UnmappedTerminologyException se algum código local não
     *     tiver correspondência registrada
     * @throws br.rndsconnect.semantic.RndsValidationException se a RN03 for violada — nos
     *     grupos 03 e 04 da Tabela SUS, a ocupação do executante é obrigatória
     * @throws br.rndsconnect.validator.FhirConformanceException se o documento resultante não
     *     for conforme ao perfil do RIRA
     * @throws IllegalArgumentException se faltar campo que o perfil exige estruturalmente
     */
    DocumentoPreparado prepararRegulacaoAssistencial(RegistroRegulacaoAssistencialDTO registro);

    /**
     * Prepara um registro de dispensação de medicamento (serviço REDFM).
     *
     * <p>Único dos serviços cujo Bundle usa <strong>recursos contidos</strong>: o
     * {@code MedicationDispense} carrega até quatro, com {@code id} fixado pelo perfil e
     * referências por {@code #id}.
     *
     * <p>A receita vem de uma de duas formas, mutuamente exclusivas: referenciada na RNDS por
     * identificador, ou embutida como {@code contained}. Informar as duas, ou nenhuma, é erro
     * de construção.
     *
     * @param registro dados no vocabulário do sistema cliente
     * @return documento validado e trilha de auditoria
     * @throws br.rndsconnect.semantic.UnmappedTerminologyException se algum código local não
     *     tiver correspondência registrada
     * @throws br.rndsconnect.validator.FhirConformanceException se o documento resultante não
     *     for conforme ao perfil do REDFM
     * @throws IllegalArgumentException se faltar campo que o perfil exige, ou se as duas
     *     formas de receita forem informadas ao mesmo tempo
     */
    DocumentoPreparado prepararDispensacaoMedicamento(RegistroDispensacaoMedicamentoDTO registro);

    /**
     * Prepara um Sumário de Alta (serviço SA).
     *
     * <p>Até quinze entradas, dois {@code Composition} — o
     * sumário e a prescrição de alta embutida, do tipo RPM — e dez seções.
     *
     * @param registro dados no vocabulário do sistema cliente
     * @return documento validado e trilha de auditoria
     * @throws br.rndsconnect.semantic.UnmappedTerminologyException se algum código local não
     *     tiver correspondência registrada
     * @throws br.rndsconnect.validator.FhirConformanceException se o documento resultante não
     *     for conforme ao perfil do SA
     * @throws IllegalArgumentException se faltar campo que o perfil exige
     */
    DocumentoPreparado prepararSumarioAlta(RegistroSumarioAltaDTO registro);

    /**
     * Prepara um Registro de Atendimento Clínico (serviço RAC).
     *
     * <p>Vinte e quatro entradas e dezoito seções no caso completo, e três seções no mínimo —
     * seis das oito seções do documento são opcionais, mais do que em qualquer outro serviço
     * do escopo.
     *
     * <p>Um aviso que a assinatura não dá: o perfil não restringe o vocabulário do valor
     * codificado das medidas observadas, e a Camada 4 aprova qualquer par ali. Peso em metros
     * passa.
     *
     * @param registro dados no vocabulário do sistema cliente
     * @return documento validado e trilha de auditoria
     * @throws br.rndsconnect.semantic.UnmappedTerminologyException se algum código local não
     *     tiver correspondência registrada
     * @throws br.rndsconnect.validator.FhirConformanceException se o documento resultante não
     *     for conforme ao perfil do RAC
     * @throws IllegalArgumentException se faltar campo que o perfil exige
     */
    DocumentoPreparado prepararAtendimentoClinico(RegistroAtendimentoClinicoDTO registro);
}
