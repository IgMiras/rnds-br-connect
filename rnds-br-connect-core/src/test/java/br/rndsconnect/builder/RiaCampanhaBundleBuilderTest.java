package br.rndsconnect.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rndsconnect.model.CodedValue;
import br.rndsconnect.model.EnrichedRegistroImunizacaoCampanha;
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
import org.hl7.fhir.r4.model.Immunization;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Critério de saída do {@link RiaCampanhaBundleBuilder}, e verificação empírica do ADR-06.
 *
 * <p>O teste que carrega o argumento arquitetural é
 * {@link #perfilRejeitaSiteERoute()}: ele demonstra que os elementos <strong>obrigatórios</strong>
 * no RIA-R são <strong>proibidos</strong> no RIA-C. Herança entre os dois Builders faria a
 * subclasse herdar a construção de campos que a RNDS recusa.
 *
 * @author Igor Mendes Domingues Miras
 */
class RiaCampanhaBundleBuilderTest {

    private static final String SOLICITANTE = "1";
    private static final String CPF = "01234567890";
    private static final String CNS_PROFISSIONAL = "012345678912345";
    /** {@code Composition.date} — 2024-04-01T08:18:30-03:00. */
    private static final Instant DATA_REGISTRO = Instant.parse("2024-04-01T11:18:30Z");
    /** {@code Bundle.timestamp} — 2024-04-01T14:59:51.283-03:00. */
    private static final Instant MONTAGEM = Instant.parse("2024-04-01T17:59:51.283Z");

    private static FhirContext ctx;
    private static LocalFhirValidator validador;
    private static IParser parser;

    @BeforeAll
    static void carregar() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        ctx = FhirContext.forR4();
        validador = new LocalFhirValidator(ctx, ConformancePackage.paraServico(ctx, Servico.RIA, "conformance"));
        parser = ctx.newJsonParser();
    }

    /**
     * O caminho do exemplo oficial.
     *
     * <p>Não há um "mínimo" separado como no RIA-R: <strong>todo campo que o exemplo do RIA-C
     * emite é obrigatório pelo perfil</strong>. Mínimo e completo coincidem — consequência
     * direta de o perfil proibir tudo que é opcional na rotina.
     */
    private static EnrichedRegistroImunizacaoCampanha.EnrichedRegistroImunizacaoCampanhaBuilder campanha() {
        return EnrichedRegistroImunizacaoCampanha.builder()
                .idLocalRegistro("012345678")
                .cpfPaciente(CPF)
                .cnesEstabelecimento("0123456")
                .cnsProfissional(CNS_PROFISSIONAL)
                .dataRegistroOrigem(DATA_REGISTRO)
                .dataAplicacao(LocalDate.of(2024, 4, 1))
                .grupoAtendimento(CodedValue.rnds("BRGrupoAtendimento", "000703"))
                .condicaoMaternal(CodedValue.rnds("BRCondicaoMaternal", "1"))
                .vacina(CodedValue.rnds("BRImunobiologico", "88"))
                .doseNumber("1")
                .codigoFabricante("30587")
                .lote("220262");
    }

    private static RiaCampanhaBundleBuilder builderCom(String... uuids) {
        Deque<String> fila = new ArrayDeque<>(List.of(uuids));
        return new RiaCampanhaBundleBuilder(SOLICITANTE, fila::removeFirst, () -> MONTAGEM);
    }

    // ══ Critério de saída ═══════════════════════════════════════════════

    @Test
    @DisplayName("Reproduz ria-c-completo.json campo a campo")
    void reproduzArtefatoDeReferencia() {
        Bundle construido = builderCom("c1d2e3f4-5a6b-4c7d-8e9f-0a1b2c3d4e50", "c1d2e3f4-5a6b-4c7d-8e9f-0a1b2c3d4e51")
                .build(campanha().build());

        assertThat(canonico(construido)).isEqualTo(canonico(ler("golden/ria-c-completo.json")));
    }

    @Test
    @DisplayName("O Bundle construído passa na Camada 4")
    void bundleConstruidoEConforme() {
        Bundle b = builderCom("11111111-1111-4111-8111-111111111111", "22222222-2222-4222-8222-222222222222")
                .build(campanha().build());
        ValidationResult r = validador.validar(b);
        LocalFhirValidator.erros(r)
                .forEach(m ->
                        System.out.printf("  [%s] %s: %s%n", m.getSeverity(), m.getLocationString(), m.getMessage()));
        assertThat(LocalFhirValidator.erros(r)).isEmpty();
    }

    // ══ ADR-06: por que não há herança ══════════════════════════════════

    @Test
    @DisplayName("ADR-06: site e route são PROIBIDOS no RIA-C — o oposto do RIA-R")
    void perfilRejeitaSiteERoute() {
        Bundle b = builderCom("33333333-3333-4333-8333-333333333333", "44444444-4444-4444-8444-444444444444")
                .build(campanha().build());

        // Simula o que uma subclasse de RiaBundleBuilder faria: emitir site e route.
        Immunization imm = (Immunization) b.getEntry().get(1).getResource();
        imm.setSite(conceito("BRLocalAplicacao", "2"));
        imm.setRoute(conceito("BRViaAdministracao", "10890"));

        ValidationResult r = validador.validar(b);
        String erros = LocalFhirValidator.erros(r).toString();
        System.out.println("[ADR-06] herdar do RiaBundleBuilder produziria: " + erros);

        assertThat(LocalFhirValidator.erros(r))
                .as("site e route são 1..1 no BRImunobiologicoAdministrado-3.0 e max=0 no "
                        + "BRImunobiologicoAdministradoCampanha-2.0. Uma subclasse herdaria a "
                        + "construção deles e teria de desfazê-la — sinal clássico de herança "
                        + "mal aplicada.")
                .isNotEmpty();
        assertThat(erros).containsIgnoringCase("site");
        assertThat(erros).containsIgnoringCase("route");
    }

    @Test
    @DisplayName("O Bundle do RIA-C tem duas entradas e nenhuma extensão de estratégia")
    void estruturaEnxutaDaCampanha() {
        Bundle b = builderCom("55555555-5555-4555-8555-555555555555", "66666666-6666-4666-8666-666666666666")
                .build(campanha().build());

        assertThat(b.getEntry())
                .as("não há Condition: reasonReference é proibido")
                .hasSize(2);

        Immunization imm = (Immunization) b.getEntry().get(1).getResource();
        assertThat(imm.getExtension())
                .as("extension é 2..2, sem contatoHanseniase")
                .hasSize(2);
        assertThat(imm.getProtocolAppliedFirstRep().getExtension())
                .as("em campanha, a campanha É a estratégia — não há extensão de estratégia")
                .isEmpty();

        String json = canonico(b);
        assertThat(json)
                .doesNotContain("\"site\"")
                .doesNotContain("\"route\"")
                .doesNotContain("reportOrigin")
                .doesNotContain("primarySource")
                .doesNotContain("expirationDate")
                .doesNotContain("reasonReference");
    }

    // ══ A inversão de obrigatoriedade ═══════════════════════════════════

    @Test
    @DisplayName("lote e fabricante são obrigatórios aqui — e opcionais no RIA-R")
    void loteEFabricanteSaoObrigatorios() {
        assertThatThrownBy(
                        () -> builderCom("a", "b").build(campanha().lote(null).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lote")
                .hasMessageContaining("opcional no RIA-R");

        assertThatThrownBy(() -> builderCom("a", "b")
                        .build(campanha().codigoFabricante(null).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("codigoFabricante");
    }

    @Test
    @DisplayName("CANÁRIO: sem lotNumber o perfil da campanha reprova")
    void perfilRejeitaBundleSemLote() {
        Bundle b = builderCom("77777777-7777-4777-8777-777777777777", "88888888-8888-4888-8888-888888888888")
                .build(campanha().build());
        Immunization imm = (Immunization) b.getEntry().get(1).getResource();
        imm.setLotNumber(null);

        ValidationResult r = validador.validar(b);
        assertThat(LocalFhirValidator.erros(r))
                .as("lotNumber é 0..1 no RIA-R e 1..1 no RIA-C: só reprova se o perfil de "
                        + "campanha estiver efetivamente aplicado")
                .isNotEmpty();
        assertThat(LocalFhirValidator.erros(r).toString()).containsIgnoringCase("lotNumber");
    }

    @Test
    @DisplayName("O título fixo distingue os dois documentos")
    void tituloDistingueOsServicos() {
        Bundle b = builderCom("99999999-9999-4999-8999-999999999999", "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .build(campanha().build());
        org.hl7.fhir.r4.model.Composition c =
                (org.hl7.fhir.r4.model.Composition) b.getEntry().get(0).getResource();
        assertThat(c.getTitle()).isEqualTo("Registro de Imunobiologico Administrado na Campanha");
        assertThat(c.getMeta().getProfile().get(0).getValue()).endsWith("Campanha-2.0");
    }

    // ══ Auxiliares ══════════════════════════════════════════════════════

    private static org.hl7.fhir.r4.model.CodeableConcept conceito(String cs, String code) {
        org.hl7.fhir.r4.model.CodeableConcept cc = new org.hl7.fhir.r4.model.CodeableConcept();
        cc.addCoding()
                .setSystem("http://www.saude.gov.br/fhir/r4/CodeSystem/" + cs)
                .setCode(code);
        return cc;
    }

    private static String canonico(Bundle b) {
        return parser.setPrettyPrint(true).encodeResourceToString(b);
    }

    private static Bundle ler(String recurso) {
        try (InputStream in =
                RiaCampanhaBundleBuilderTest.class.getClassLoader().getResourceAsStream(recurso)) {
            if (in == null) {
                throw new IllegalStateException("Artefato de referência ausente: " + recurso);
            }
            return parser.parseResource(Bundle.class, new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler " + recurso, e);
        }
    }
}
