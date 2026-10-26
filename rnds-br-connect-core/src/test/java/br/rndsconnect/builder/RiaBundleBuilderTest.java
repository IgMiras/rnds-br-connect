package br.rndsconnect.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rndsconnect.model.CodedValue;
import br.rndsconnect.model.EnrichedRegistroImunizacao;
import br.rndsconnect.model.PesquisaClinica;
import br.rndsconnect.validator.ConformancePackage;
import br.rndsconnect.validator.LocalFhirValidator;
import br.rndsconnect.validator.Servico;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.validation.ValidationResult;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.TimeZone;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Critério de saída do {@link RiaBundleBuilder}: reproduzir os dois artefatos de referência
 * campo a campo, e passar na Camada 4.
 *
 * <p>A comparação é feita sobre a serialização canônica do HAPI: tanto o Bundle construído
 * quanto o artefato de referência lido do disco são reserializados pelo mesmo parser antes
 * do confronto. Isso elimina diferenças de ordem de campos e de formatação, deixando
 * qualquer divergência restante como divergência real de conteúdo.
 *
 * <p>Os UUIDs são injetados em ordem fixa para tornar a construção determinística — sem
 * isso, comparar com um arquivo em disco seria impossível.
 *
 * @author Igor Mendes Domingues Miras
 */
class RiaBundleBuilderTest {

    private static final String SD = "http://www.saude.gov.br/fhir/r4/StructureDefinition/";
    private static final String SOLICITANTE = "29376";
    private static final String CPF = "01234567890";
    private static final String CNS_PROFISSIONAL = "012345678912345";
    /** {@code Composition.date} — 2024-04-01T08:18:30-03:00 no exemplo oficial. */
    private static final Instant DATA_REGISTRO = Instant.parse("2024-04-01T11:18:30Z");
    /** {@code Bundle.timestamp} — 2024-04-01T14:59:51.283-03:00, instante da montagem. */
    private static final Instant MONTAGEM = Instant.parse("2024-04-01T17:59:51.283Z");
    /** {@code Condition.onsetDateTime} — 2024-03-30T14:59:51.283-03:00. */
    private static final Instant INICIO_CONDICAO = Instant.parse("2024-03-30T17:59:51.283Z");

    private static FhirContext ctx;
    private static LocalFhirValidator validador;
    private static IParser parser;

    @BeforeAll
    static void carregar() {
        // O HAPI serializa datas no fuso da JVM. Fixá-lo torna a comparação com os
        // artefatos de referência independente da máquina que roda a suíte.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        ctx = FhirContext.forR4();
        validador = new LocalFhirValidator(ctx, ConformancePackage.paraServico(ctx, Servico.RIA, "conformance"));
        parser = ctx.newJsonParser();
    }

    // ══ Fixtures ════════════════════════════════════════════════════════

    /** Caminho mínimo: apenas o que as cardinalidades do perfil exigem. */
    private static EnrichedRegistroImunizacao.EnrichedRegistroImunizacaoBuilder minimo() {
        return EnrichedRegistroImunizacao.builder()
                .idLocalRegistro("MINIMO-0001")
                .cpfPaciente(CPF)
                .cnesEstabelecimento("0123456")
                .cnsProfissional(CNS_PROFISSIONAL)
                .dataRegistroOrigem(DATA_REGISTRO)
                .grupoAtendimento(CodedValue.rnds("BRGrupoAtendimento", "000703"))
                .condicaoMaternal(CodedValue.rnds("BRCondicaoMaternal", "1"))
                .estrategiaVacinacao(CodedValue.rnds("BREstrategiaVacinacao", "1"))
                .vacina(CodedValue.rnds("BRImunobiologico", "55"))
                .localAplicacao(CodedValue.rnds("BRLocalAplicacao", "0"))
                .viaAdministracao(CodedValue.rnds("BRViaAdministracao", "0"))
                .doseNumber("1")
                .dataAplicacao(LocalDate.of(2024, 4, 1));
    }

    /** Caminho máximo: tudo que o perfil permite, espelhando o exemplo oficial. */
    private static EnrichedRegistroImunizacao completo() {
        return minimo().idLocalRegistro("012345678")
                .codigoFabricante("152")
                .lote("220262")
                .validadeLote(LocalDate.of(2025, 5, 10))
                .registroOrigem(CodedValue.rnds("BRRegistroOrigem", "01"))
                .primarySource(false)
                .codigoPaisOrigem("10")
                .cboPrescritor(CodedValue.rnds("BRCBO", "225103"))
                .contatoHanseniase(false)
                .cid10(CodedValue.rnds("BRCID10", "B15"))
                .dataInicioCondicao(INICIO_CONDICAO)
                .pesquisaClinica(PesquisaClinica.builder()
                        .numeroProtocoloEstudoAnvisa("GU1AMT9CYR")
                        .numeroVersaoProtocoloEstudo("F3D1U354V6")
                        .numeroRegistroVacinaAnvisa("WWZZJJWIBF")
                        .build())
                .build();
    }

    private static RiaBundleBuilder builderCom(String... uuids) {
        Deque<String> fila = new ArrayDeque<>(List.of(uuids));
        return new RiaBundleBuilder(SOLICITANTE, fila::removeFirst, () -> MONTAGEM);
    }

    // ══ Testes ══════════════════════════════════════════════════════════

    @Test
    @DisplayName("Caminho mínimo reproduz ria-r-minimo.json campo a campo")
    void minimoReproduzArtefatoDeReferencia() {
        Bundle construido = builderCom("3e7d9c25-8b41-4a6f-b0d2-7c9e5a1f3b40", "3e7d9c25-8b41-4a6f-b0d2-7c9e5a1f3b41")
                .build(minimo().build());

        assertThat(canonico(construido)).isEqualTo(canonico(ler("golden/ria-r-minimo.json")));
    }

    @Test
    @DisplayName("Caminho completo reproduz ria-r-completo.json campo a campo")
    void completoReproduzArtefatoDeReferencia() {
        Bundle construido = builderCom(
                        "0a8f6b1e-5c74-4d29-9e83-1f2a3b4c5d60",
                        "0a8f6b1e-5c74-4d29-9e83-1f2a3b4c5d61",
                        "0a8f6b1e-5c74-4d29-9e83-1f2a3b4c5d62")
                .build(completo());

        assertThat(canonico(construido)).isEqualTo(canonico(ler("golden/ria-r-completo.json")));
    }

    @Test
    @DisplayName("O Bundle construído passa na Camada 4 — mínimo e completo")
    void bundlesConstruidosSaoConformes() {
        Bundle min = builderCom("11111111-1111-4111-8111-111111111111", "22222222-2222-4222-8222-222222222222")
                .build(minimo().build());
        ValidationResult rMin = validador.validar(min);
        imprimir("construído: mínimo", rMin);
        assertThat(LocalFhirValidator.erros(rMin)).isEmpty();

        Bundle comp = builderCom(
                        "33333333-3333-4333-8333-333333333333",
                        "44444444-4444-4444-8444-444444444444",
                        "55555555-5555-4555-8555-555555555555")
                .build(completo());
        ValidationResult rComp = validador.validar(comp);
        imprimir("construído: completo", rComp);
        assertThat(LocalFhirValidator.erros(rComp)).isEmpty();
    }

    @Test
    @DisplayName("Sem CID, o Bundle tem 2 entradas e nenhum reasonReference")
    void semCidNaoEmiteCondition() {
        Bundle b = builderCom("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
                .build(minimo().build());

        assertThat(b.getEntry()).hasSize(2);
        assertThat(canonico(b)).doesNotContain("reasonReference").doesNotContain("Condition");
    }

    @Test
    @DisplayName("Com CID, o Condition entra por reasonReference — nunca por section.entry")
    void condicaoEntraPorReasonReference() {
        String uuidCondition = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";
        Bundle b = builderCom(
                        "dddddddd-dddd-4ddd-8ddd-dddddddddddd", "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee", uuidCondition)
                .build(completo());

        assertThat(b.getEntry()).hasSize(3);

        org.hl7.fhir.r4.model.Composition composition =
                (org.hl7.fhir.r4.model.Composition) b.getEntry().get(0).getResource();
        assertThat(composition.getSectionFirstRep().getEntry())
                .as("Composition.section é 1..1 e aponta para o Immunization, não para o Condition")
                .hasSize(1);
        assertThat(composition.getSectionFirstRep().getEntryFirstRep().getReference())
                .doesNotContain(uuidCondition);

        org.hl7.fhir.r4.model.Immunization imm =
                (org.hl7.fhir.r4.model.Immunization) b.getEntry().get(1).getResource();
        assertThat(imm.getReasonReferenceFirstRep().getReference()).isEqualTo("urn:uuid:" + uuidCondition);
    }

    @Test
    @DisplayName("Todo fullUrl emitido é urn:uuid com UUID válido da RFC 4122")
    void fullUrlUsaUuidValido() {
        Bundle b = builderCom(
                        "0a8f6b1e-5c74-4d29-9e83-1f2a3b4c5d60",
                        "0a8f6b1e-5c74-4d29-9e83-1f2a3b4c5d61",
                        "0a8f6b1e-5c74-4d29-9e83-1f2a3b4c5d62")
                .build(completo());

        assertThat(b.getEntry()).allSatisfy(e -> assertThat(e.getFullUrl())
                .as("os exemplos oficiais usam urn:uuid:transient-N, que o validador reprova "
                        + "(§8.1.2, defeito nº 6)")
                .matches("urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));
    }

    @Test
    @DisplayName("Campo obrigatório ausente falha na construção, não na validação")
    void obrigatorioAusenteFalhaCedo() {
        assertThatThrownBy(() -> builderCom("f".repeat(8) + "-ffff-4fff-8fff-" + "f".repeat(12))
                        .build(minimo().viaAdministracao(null).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("viaAdministracao");
    }

    // ══ Auxiliares ══════════════════════════════════════════════════════

    private static String canonico(Bundle b) {
        return parser.setPrettyPrint(true).encodeResourceToString(b);
    }

    private static Bundle ler(String recurso) {
        try (InputStream in = RiaBundleBuilderTest.class.getClassLoader().getResourceAsStream(recurso)) {
            if (in == null) {
                throw new IllegalStateException("Artefato de referência ausente: " + recurso);
            }
            return parser.parseResource(Bundle.class, new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler " + recurso, e);
        }
    }

    private static void imprimir(String rotulo, ValidationResult resultado) {
        System.out.println("--- " + rotulo + " ---");
        LocalFhirValidator.erros(resultado)
                .forEach(m ->
                        System.out.printf("  [%s] %s: %s%n", m.getSeverity(), m.getLocationString(), m.getMessage()));
    }
}
