package br.rndsconnect.semantic.regras;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rndsconnect.builder.RelBundleBuilder;
import br.rndsconnect.builder.RiaBundleBuilder;
import br.rndsconnect.dto.RegistroExameLaboratorialDTO;
import br.rndsconnect.model.CodedValue;
import br.rndsconnect.model.EnrichedRegistroExameLaboratorial;
import br.rndsconnect.model.EnrichedRegistroExameLaboratorial.ExameLaboratorial;
import br.rndsconnect.model.EnrichedRegistroImunizacao;
import br.rndsconnect.semantic.CadeiaDeValidacao;
import br.rndsconnect.semantic.RndsValidationException;
import br.rndsconnect.validator.ConformancePackage;
import br.rndsconnect.validator.LocalFhirValidator;
import br.rndsconnect.validator.Servico;
import ca.uhn.fhir.context.FhirContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regras de negócio da Frente A — as que vivem na prosa dos manuais e que nenhum perfil FHIR
 * expressa.
 *
 * <p>Dois testes carregam o argumento da camada:
 * {@link #rn3QuantitativoSemInterpretacaoPassaNaCamadaQuatro()} e
 * {@link #estrategiaEspecialSemCidPassaNaCamadaQuatro()}. Ambos <strong>afirmam que a Camada 4
 * aprova</strong> um documento que viola o Modelo de Informação. É a mesma demonstração que o
 * teste do código {@code "105"} faz para a Frente B, aplicada a regras em vez de vocabulário:
 * <em>estrutura correta não implica conteúdo correto</em>.
 *
 * @author Igor Mendes Domingues Miras
 */
class RegrasDeNegocioTest {

    private static final String CNS = "898005900000003";
    private static final String CPF = "01234567890";
    private static final Instant QUANDO = Instant.parse("2024-04-01T11:18:30Z");
    private static final AtomicInteger SEQ = new AtomicInteger();

    private static LocalFhirValidator validador;

    @BeforeAll
    static void carregar() {
        FhirContext ctx = FhirContext.forR4();
        validador = new LocalFhirValidator(ctx, ConformancePackage.paraServico(ctx, Servico.REL, "conformance"));
    }

    private static java.util.function.Supplier<String> uuids() {
        return () -> String.format("bb000000-0000-4000-8000-%012d", SEQ.incrementAndGet());
    }

    // ══ Elo 6 — RN3 do manual REL ═══════════════════════════════════════

    @Test
    @DisplayName("RN3: laudo quantitativo sem interpretação é APROVADO pela Camada 4")
    void rn3QuantitativoSemInterpretacaoPassaNaCamadaQuatro() {
        Bundle bundle = new RelBundleBuilder("29376", uuids(), () -> QUANDO)
                .build(EnrichedRegistroExameLaboratorial.builder()
                        .idLocalRegistro("LAUDO-RN3")
                        .cnsPaciente(CNS)
                        .cnpjLaboratorio("00394544000185")
                        .cnesEstabelecimento("0123456")
                        .dataRegistroOrigem(QUANDO)
                        .suspeitaDiagnostica(CodedValue.rnds("BRCID10", "B04"))
                        .exames(List.of(ExameLaboratorial.builder()
                                .nomeExame(CodedValue.rnds("BRNomeExameLOINC", "94309-2"))
                                .categoriaExame(CodedValue.rnds("BRSubgrupoTabelaSUS", "0202"))
                                .tipoAmostra(CodedValue.rnds("BRTipoAmostraGAL", "SWNAFA"))
                                .dataColeta(QUANDO)
                                .dataLiberacaoLaudo(QUANDO.plusSeconds(3600))
                                .metodo("RT-PCR em tempo real.")
                                .valorReferencia("Não detectável em não infectados.")
                                .resultadoQuantitativo(new BigDecimal("4.7"))
                                .unidadeMedida("log10 cópias/mL")
                                // interpretacao AUSENTE — Observation.interpretation é 0..1
                                .build()))
                        .build());

        assertThat(LocalFhirValidator.erros(validador.validar(bundle)))
                .as("Observation.interpretation é 0..1 no perfil: uma carga viral sem leitura "
                        + "clínica é estruturalmente impecável")
                .isEmpty();

        System.out.println("[Frente A] laudo quantitativo sem interpretação: APROVADO pela Camada 4 "
                + "e em violação da RN3 do manual REL.");
    }

    @Test
    @DisplayName("RN3: e é REPROVADO pela Frente A, antes de qualquer construção FHIR")
    void rn3ReprovadoPelaFrenteA() {
        CadeiaDeValidacao<RegistroExameLaboratorialDTO> a1 = RegrasPadrao.exameLaboratorialA1();

        assertThatThrownBy(() -> a1.validar(laudoLocal(exameLocal()
                        .resultadoQualitativoLocal(null)
                        .resultadoQuantitativo(new BigDecimal("4.7"))
                        .unidadeMedida("log10 cópias/mL")
                        .build())))
                .isInstanceOf(RndsValidationException.class)
                .hasMessageContaining("ELO-6/RN3-INTERPRETACAO")
                .hasMessageContaining("exige interpretação");

        // Com interpretação, passa.
        assertThatCode(() -> a1.validar(laudoLocal(exameLocal()
                        .resultadoQualitativoLocal(null)
                        .resultadoQuantitativo(new BigDecimal("4.7"))
                        .unidadeMedida("log10 cópias/mL")
                        .interpretacaoLocal("DETECTAVEL")
                        .build())))
                .doesNotThrowAnyException();
    }

    // ══ Elo 9 — estratégia Especial ═════════════════════════════════════

    @Test
    @DisplayName("Elo 9: estratégia Especial sem CID é APROVADA pela Camada 4")
    void estrategiaEspecialSemCidPassaNaCamadaQuatro() {
        Bundle bundle = new RiaBundleBuilder("29376", uuids(), () -> QUANDO)
                .build(imunizacaoEnriquecida()
                        .estrategiaVacinacao(CodedValue.rnds("BREstrategiaVacinacao", "2"))
                        .build()); // sem cid10, sem cboPrescritor

        assertThat(LocalFhirValidator.erros(validador.validar(bundle)))
                .as("reasonReference e performer.function são 0..1 no perfil: a regra do "
                        + "Modelo de Informação não tem como ser expressa em FHIR")
                .isEmpty();

        System.out.println("[Frente A] estratégia Especial sem CID nem CBO: APROVADA pela Camada 4.");
    }

    @Test
    @DisplayName("Elo 9: e é REPROVADA pela Frente A — primeiro o CID, depois o CBO")
    void elo9ReprovadoPelaFrenteA() {
        CadeiaDeValidacao<EnrichedRegistroImunizacao> a2 = RegrasPadrao.imunizacaoRotinaA2();
        CodedValue especial = CodedValue.rnds("BREstrategiaVacinacao", "2");

        assertThatThrownBy(() -> a2.validar(
                        imunizacaoEnriquecida().estrategiaVacinacao(especial).build()))
                .isInstanceOf(RndsValidationException.class)
                .hasMessageContaining("ELO-9/ESTRATEGIA-ESPECIAL")
                .hasMessageContaining("cid10Local");

        assertThatThrownBy(() -> a2.validar(imunizacaoEnriquecida()
                        .estrategiaVacinacao(especial)
                        .cid10(CodedValue.rnds("BRCID10", "B15"))
                        .build()))
                .isInstanceOf(RndsValidationException.class)
                .hasMessageContaining("cboPrescritorLocal");

        assertThatCode(() -> a2.validar(imunizacaoEnriquecida()
                        .estrategiaVacinacao(especial)
                        .cid10(CodedValue.rnds("BRCID10", "B15"))
                        .cboPrescritor(CodedValue.rnds("BRCBO", "225103"))
                        .build()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Elo 9 não interfere na estratégia Rotina")
    void elo9SoValeNaEstrategiaEspecial() {
        assertThatCode(() -> RegrasPadrao.imunizacaoRotinaA2()
                        .validar(imunizacaoEnriquecida().build()))
                .as("estratégia \"1\" (Rotina) dispensa CID e CBO")
                .doesNotThrowAnyException();
    }

    // ══ Elo 8 — transcrição de caderneta ════════════════════════════════

    @Test
    @DisplayName("Elo 8: fora de transcrição, fabricante e lote são exigidos — o perfil não os exige")
    void elo8ExigeForaDeTranscricao() {
        CadeiaDeValidacao<EnrichedRegistroImunizacao> a2 = RegrasPadrao.imunizacaoRotinaA2();

        assertThatThrownBy(() -> a2.validar(
                        imunizacaoEnriquecida().codigoFabricante(null).build()))
                .isInstanceOf(RndsValidationException.class)
                .hasMessageContaining("ELO-8/TRANSCRICAO-CADERNETA")
                .hasMessageContaining("fabricante é obrigatório");

        assertThatThrownBy(() -> a2.validar(imunizacaoEnriquecida().lote(null).build()))
                .isInstanceOf(RndsValidationException.class)
                .hasMessageContaining("farmacovigilância");
    }

    @Test
    @DisplayName("Elo 8: em transcrição de caderneta, os dois são liberados")
    void elo8RelaxaEmTranscricao() {
        assertThatCode(() -> RegrasPadrao.imunizacaoRotinaA2()
                        .validar(imunizacaoEnriquecida()
                                .registroOrigem(CodedValue.rnds("BRRegistroOrigem", "01"))
                                .codigoFabricante(null)
                                .lote(null)
                                .build()))
                .as("sem este elo, o framework não conseguiria representar uma transcrição "
                        + "legítima — a caderneta de papel não traz lote nem fabricante")
                .doesNotThrowAnyException();
    }

    // ══ Elo 4 — exclusividade do resultado ══════════════════════════════

    @Test
    @DisplayName("Elo 4: o diagnóstico traz o índice do exame, não um caminho FHIR")
    void elo4IdentificaOExame() {
        CadeiaDeValidacao<RegistroExameLaboratorialDTO> a1 = RegrasPadrao.exameLaboratorialA1();

        RegistroExameLaboratorialDTO doisExames = RegistroExameLaboratorialDTO.builder()
                .idLocalRegistro("LAUDO-ELO4")
                .cpfPaciente(CPF)
                .cnpjLaboratorio("00394544000185")
                .cnesEstabelecimento("0123456")
                .dataRegistroOrigem(QUANDO)
                .suspeitaDiagnosticaLocal("MONKEYPOX")
                .exames(List.of(
                        exameLocal().build(),
                        exameLocal()
                                .resultadoQuantitativo(new BigDecimal("4.7"))
                                .unidadeMedida("log10 cópias/mL")
                                .build()))
                .build();

        assertThatThrownBy(() -> a1.validar(doisExames))
                .isInstanceOf(RndsValidationException.class)
                .hasMessageContaining("exames[1].resultado")
                .hasMessageContaining("qualitativo E quantitativo");
    }

    // ══ Elos 3 e 3b aplicados por exame ═════════════════════════════════

    @Test
    @DisplayName("Laudo liberado antes da coleta é reprovado, com o índice")
    void inversaoCausalPorExame() {
        assertThatThrownBy(() -> RegrasPadrao.exameLaboratorialA1()
                        .validar(laudoLocal(exameLocal()
                                .dataColeta(QUANDO)
                                .dataLiberacaoLaudo(QUANDO.minusSeconds(3600))
                                .build())))
                .isInstanceOf(RndsValidationException.class)
                .hasMessageContaining("exames[0].dataLiberacaoLaudo")
                .hasMessageContaining("inversão causal");
    }

    // ══ Fixtures ════════════════════════════════════════════════════════

    private static RegistroExameLaboratorialDTO.ExameDTO.ExameDTOBuilder exameLocal() {
        return RegistroExameLaboratorialDTO.ExameDTO.builder()
                .nomeExameLocal("COVID_PCR")
                .categoriaExameLocal("LAB_CLINICO")
                .tipoAmostraLocal("SWAB_NASOFARINGE")
                .dataColeta(QUANDO)
                .dataLiberacaoLaudo(QUANDO.plusSeconds(3600))
                .metodo("RT-PCR em tempo real.")
                .valorReferencia("Não detectável em não infectados.")
                .resultadoQualitativoLocal("NAO_DETECTAVEL");
    }

    private static RegistroExameLaboratorialDTO laudoLocal(RegistroExameLaboratorialDTO.ExameDTO exame) {
        return RegistroExameLaboratorialDTO.builder()
                .idLocalRegistro("LAUDO-TESTE")
                .cpfPaciente(CPF)
                .cnpjLaboratorio("00394544000185")
                .cnesEstabelecimento("0123456")
                .dataRegistroOrigem(QUANDO)
                .suspeitaDiagnosticaLocal("MONKEYPOX")
                .exames(List.of(exame))
                .build();
    }

    private static EnrichedRegistroImunizacao.EnrichedRegistroImunizacaoBuilder imunizacaoEnriquecida() {
        return EnrichedRegistroImunizacao.builder()
                .idLocalRegistro("REG-FRENTE-A")
                .cpfPaciente(CPF)
                .cnesEstabelecimento("0123456")
                .cnsProfissional(CNS)
                .dataRegistroOrigem(QUANDO)
                .dataAplicacao(LocalDate.of(2024, 4, 1))
                .grupoAtendimento(CodedValue.rnds("BRGrupoAtendimento", "000703"))
                .condicaoMaternal(CodedValue.rnds("BRCondicaoMaternal", "1"))
                .estrategiaVacinacao(CodedValue.rnds("BREstrategiaVacinacao", "1"))
                .vacina(CodedValue.rnds("BRImunobiologico", "55"))
                .localAplicacao(CodedValue.rnds("BRLocalAplicacao", "0"))
                .viaAdministracao(CodedValue.rnds("BRViaAdministracao", "0"))
                .doseNumber("1")
                .codigoFabricante("152")
                .lote("220262");
    }
}
