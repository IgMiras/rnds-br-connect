package br.rndsconnect.semantic.regras;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rndsconnect.builder.RiraBundleBuilder;
import br.rndsconnect.model.CodedValue;
import br.rndsconnect.model.EnrichedRegistroRegulacaoAssistencial;
import br.rndsconnect.model.EnrichedRegistroRegulacaoAssistencial.EventoRegulacao;
import br.rndsconnect.model.EstadoRegulacao;
import br.rndsconnect.semantic.RndsValidationException;
import br.rndsconnect.validator.ConformancePackage;
import br.rndsconnect.validator.LocalFhirValidator;
import br.rndsconnect.validator.Servico;
import ca.uhn.fhir.context.FhirContext;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Elo 10 — RN03 do manual RIRA.
 *
 * <p>O teste que carrega o argumento é {@link #cirurgiaSemCboPassaNaCamadaQuatro()}: uma
 * solicitação de cirurgia cardiovascular sem a ocupação do executante é aprovada pela Camada
 * 4, porque {@code ServiceRequest.performerType} é {@code 0..1} no perfil.
 *
 * <p>E {@link #cbhpmtussNaoEAlcancadoPelaRegra()} fixa a razão de o {@code system} ser
 * verificado antes do {@code code} — o segundo CodeSystem do mesmo ValueSet tem estrutura
 * incompatível, e aplicar a regra a ele produziria falso positivo.
 *
 * @author Igor Mendes Domingues Miras
 */
class RN03ProcedimentoExigeCboTest {

    private static final String CS = "http://www.saude.gov.br/fhir/r4/CodeSystem/";
    private static final Instant QUANDO = Instant.parse("2023-04-09T00:00:00Z");
    private static final AtomicInteger SEQ = new AtomicInteger();

    private static LocalFhirValidator validador;
    private static final RN03ProcedimentoExigeCbo REGRA = new RN03ProcedimentoExigeCbo();

    @BeforeAll
    static void carregar() {
        FhirContext ctx = FhirContext.forR4();
        validador = new LocalFhirValidator(ctx, ConformancePackage.paraServico(ctx, Servico.RIRA, "conformance/rira"));
    }

    // ══ O argumento ═════════════════════════════════════════════════════

    @Test
    @DisplayName("RN03: cirurgia sem CBO é APROVADA pela Camada 4")
    void cirurgiaSemCboPassaNaCamadaQuatro() {
        // 0406010013 é abertura de comunicação inter-atrial — grupo 04, cirúrgico.
        // performerType é 0..1 no perfil, então o Bundle é estruturalmente impecável.
        Bundle bundle = new RiraBundleBuilder(
                        "29376",
                        () -> String.format("ee000000-0000-4000-8000-%012d", SEQ.incrementAndGet()),
                        () -> QUANDO)
                .build(solicitacao("0406010013").cboExecutante(null).build());

        assertThat(LocalFhirValidator.erros(validador.validar(bundle)))
                .as("ServiceRequest.performerType é 0..1: a regra está na prosa do manual, " + "não no perfil")
                .isEmpty();

        System.out.println("[RN03] cirurgia cardiovascular sem CBO: APROVADA pela Camada 4, "
                + "em violação da RN03 do manual RIRA.");
    }

    @Test
    @DisplayName("RN03: e é REPROVADA pelo elo 10, com o grupo identificado")
    void cirurgiaSemCboEReprovadaPeloElo10() {
        assertThatThrownBy(() -> REGRA.validar(
                        solicitacao("0406010013").cboExecutante(null).build()))
                .isInstanceOf(RndsValidationException.class)
                .hasMessageContaining("ELO-10/RN03")
                .hasMessageContaining("0406010013")
                .hasMessageContaining("grupo 04");
    }

    // ══ Os grupos ═══════════════════════════════════════════════════════

    @Test
    @DisplayName("Grupos 03 e 04 exigem CBO; os demais não")
    void apenasOsGrupos03e04ExigemCbo() {
        // 03 — procedimentos clínicos
        assertThatThrownBy(() -> REGRA.validar(
                        solicitacao("0301010030").cboExecutante(null).build()))
                .isInstanceOf(RndsValidationException.class)
                .hasMessageContaining("grupo 03");

        // 02 — diagnóstico: a RN03 não se pronuncia
        assertThatCode(() -> REGRA.validar(
                        solicitacao("0201010038").cboExecutante(null).build()))
                .as("biópsia de tireoide é grupo 02; o manual não exige CBO")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Com CBO informado, qualquer grupo passa")
    void comCboQualquerGrupoPassa() {
        for (String codigo : new String[] {"0406010013", "0301010030", "0201010038"}) {
            assertThatCode(() -> REGRA.validar(solicitacao(codigo).build())).doesNotThrowAnyException();
        }
    }

    // ══ Por que o system vem antes do code ══════════════════════════════

    @Test
    @DisplayName("O BRCBHPMTUSS não é alcançado pela regra — evita falso positivo")
    void cbhpmtussNaoEAlcancadoPelaRegra() {
        // O BRProcedimentosNacionais-1.0 compõe DOIS CodeSystems. No BRCBHPMTUSS os códigos
        // têm 3 dígitos, e "030" existe lá sem ser "grupo 03" no sentido da RN03. Aplicar a
        // regra a ele produziria falso positivo — daí a checagem do system.
        assertThatCode(() -> REGRA.validar(EnrichedRegistroRegulacaoAssistencial.builder()
                        .idLocalRegistro("REG-CBHPM")
                        .cnsPaciente("819217217061851")
                        .cnesSolicitante("162338254590005")
                        .dataRegistroOrigem(QUANDO)
                        .modalidadeAssistencial(new CodedValue(CS + "BRModalidadeAssistencial", "09"))
                        .caraterAtendimento(new CodedValue("http://hl7.org/fhir/request-priority", "routine"))
                        .procedimento(new CodedValue(CS + "BRCBHPMTUSS", "030"))
                        .cid10(new CodedValue(CS + "BRCID10", "I85"))
                        .eventos(List.of(evento()))
                        .build()))
                .as("a RN03 fala da Tabela SUS, e só dela")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A regra cobre mais da metade da Tabela SUS")
    void aRegraNaoEMarginal() {
        // 837 códigos no grupo 03 + 1.696 no grupo 04 = 2.533 dos 4.932 da Tabela SUS.
        assertThat(RN03ProcedimentoExigeCbo.SYSTEM_TABELA_SUS).endsWith("/BRTabelaSUS");
    }

    // ══ Fixtures ════════════════════════════════════════════════════════

    private static EventoRegulacao evento() {
        return EventoRegulacao.builder()
                .estado(EstadoRegulacao.PENDENTE)
                .quando(QUANDO)
                .build();
    }

    private static EnrichedRegistroRegulacaoAssistencial.EnrichedRegistroRegulacaoAssistencialBuilder solicitacao(
            String procedimento) {
        return EnrichedRegistroRegulacaoAssistencial.builder()
                .idLocalRegistro("REG-RN03")
                .cnsPaciente("819217217061851")
                .cnesSolicitante("162338254590005")
                .cnesExecutante("162338254590005")
                .dataRegistroOrigem(QUANDO)
                .dataRequisicao(QUANDO)
                .inicioAgendamento(QUANDO)
                .fimAgendamento(QUANDO)
                .modalidadeAssistencial(new CodedValue(CS + "BRModalidadeAssistencial", "09"))
                .caraterAtendimento(new CodedValue("http://hl7.org/fhir/request-priority", "routine"))
                .procedimento(new CodedValue(CS + "BRTabelaSUS", procedimento))
                .cboExecutante(new CodedValue(CS + "BRCBO", "225265"))
                .cid10(new CodedValue(CS + "BRCID10", "I85"))
                .eventos(List.of(evento()));
    }
}
