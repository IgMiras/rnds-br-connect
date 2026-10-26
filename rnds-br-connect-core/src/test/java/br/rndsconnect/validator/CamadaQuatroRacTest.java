package br.rndsconnect.validator;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Procedure;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Portão de entrada do RAC: a Camada 4 carrega o pacote {@code conformance/rac} e aprova o
 * artefato de referência — antes de existir qualquer DTO ou Builder.
 *
 * <p>O pacote vem do {@code arquivos_definicoes_SA}, e não do {@code datasus-pacote-completo},
 * por medição e não por rótulo. O perfil {@code BRRegistroAtendimentoClinico} existe nos dois
 * com a mesma URL canônica e a mesma data, em duas versões: 3.0 {@code active} no pacote
 * completo e 2.1 {@code draft} no do SA. A v3.0 discrimina a fatia {@code cadernetaCrianca} por
 * {@code entry.resolve().conformsTo('.../questionnaire/brmarcosdesenvolvimento')} — um artefato
 * que o pacote que a distribui não traz, e que os outros pacotes declaram com {@code Q}
 * maiúsculo, o que não resolve porque URL canônica é sensível a caixa. Resultado: contra o
 * pacote completo o validador do DATASUS acusa 38 erros e <strong>nenhuma seção é avaliada</strong>;
 * contra o pacote do SA, 12 erros, todos do exemplo. O ADR-09 pede a versão vigente em
 * produção, e a única verificável é a que consegue validar o documento.
 *
 * <p>Os 12 erros do exemplo oficial saem de dois pontos, ambos corrigidos no golden com valores
 * sintéticos declarados: o identificador do {@code Procedure}, que não casa com a máscara da
 * {@code cmd-1} (defeito nº 16 — a descrição da constraint e a expressão divergem, e quem manda
 * é a expressão), e o código {@code BRObmCATMAT#BR0352317}, que não existe no CodeSystem
 * distribuído. Oito dos doze erros eram cascata do primeiro: sem o {@code Procedure}
 * conformando, o {@code Encounter.diagnosis[procedure]} não casa, e sem o {@code Encounter}
 * conformando caem as duas seções obrigatórias do CMD.
 *
 * <p>O teste que importa aqui é o {@link #canarioIdentificadorForaDaMascara()}. Uma cadeia de
 * validação mal montada aprova tudo em silêncio: o HAPI cai para o resource base do R4 e nada
 * reprova. Reintroduzir o identificador original é a maneira mais barata de provar que a
 * {@code cmd-1} está sendo avaliada de fato — e, de quebra, reproduz em teste executável a
 * divergência encontrada no exemplo oficial.
 *
 * @author Igor Mendes Domingues Miras
 */
class CamadaQuatroRacTest {

    /** Identificador do Procedure como vem no exemplo oficial — reprova na {@code cmd-1}. */
    private static final String IDENTIFICADOR_ORIGINAL = "462921313170263";

    /** Índice da entrada do {@code Procedure} no artefato de referência. */
    private static final int ENTRADA_PROCEDURE = 3;

    private static FhirContext ctx;
    private static ConformancePackage pacote;
    private static LocalFhirValidator validador;

    @BeforeAll
    static void carregar() {
        ctx = FhirContext.forR4();
        pacote = ConformancePackage.paraServico(ctx, Servico.RAC, "conformance/rac");
        validador = new LocalFhirValidator(ctx, pacote);

        long semSnapshot = pacote.structureDefinitions().stream()
                .filter(sd -> !sd.hasSnapshot())
                .count();

        System.out.printf(
                "%n[Camada 4 · RAC] %d artefatos carregados (%d SD, %d VS, %d CS) em %d ms%n"
                        + "[Camada 4 · RAC] SD sem snapshot na carga: %d de %d%n"
                        + "[Camada 4 · RAC] ignorados: %s%n",
                pacote.total(),
                pacote.structureDefinitions().size(),
                pacote.valueSets().size(),
                pacote.codeSystems().size(),
                pacote.tempoCargaMillis(),
                semSnapshot,
                pacote.structureDefinitions().size(),
                pacote.ignorados());
    }

    @Test
    @DisplayName("O pacote conformance/rac carrega os perfis que o Bundle instancia")
    void pacoteCarrega() {
        assertThat(pacote.total()).isGreaterThan(100);

        List<String> urls =
                pacote.structureDefinitions().stream().map(sd -> sd.getUrl()).toList();
        assertThat(urls)
                .contains(
                        "http://www.saude.gov.br/fhir/r4/StructureDefinition/BRRegistroAtendimentoClinico",
                        "http://www.saude.gov.br/fhir/r4/StructureDefinition/BRConjuntoMinimoDados-1.1",
                        "http://www.saude.gov.br/fhir/r4/StructureDefinition/BRContatoAssistencial-1.0",
                        "http://www.saude.gov.br/fhir/r4/StructureDefinition/BRProcedimentoRealizado-1.0",
                        "http://www.saude.gov.br/fhir/r4/StructureDefinition/BRMedidaObservada",
                        "http://www.saude.gov.br/fhir/r4/StructureDefinition/BRRegistroPrescricaoMedicamento",
                        "http://www.saude.gov.br/fhir/r4/StructureDefinition/BRAtestado");
    }

    @Test
    @DisplayName("Artefato de referência do RAC é aprovado pela Camada 4")
    void goldenCompletoAprovado() {
        Bundle bundle = ler("golden/rac-completo.json");
        ValidationResult resultado = validador.validar(bundle);
        imprimir("rac-completo", resultado);
        assertThat(LocalFhirValidator.erros(resultado))
                .as("O validador desktop do DATASUS aprova este mesmo arquivo com o pacote "
                        + "completo do SA. Se o HAPI local discordar, a divergência é da cadeia "
                        + "da Camada 4, não do documento.")
                .isEmpty();
    }

    @Test
    @DisplayName("CANÁRIO: identificador fora da máscara da cmd-1 reprova — se passar, a validação é inócua")
    void canarioIdentificadorForaDaMascara() {
        Bundle bundle = ler("golden/rac-completo.json");
        Procedure procedimento =
                (Procedure) bundle.getEntry().get(ENTRADA_PROCEDURE).getResource();
        procedimento.getIdentifierFirstRep().setValue(IDENTIFICADOR_ORIGINAL);

        ValidationResult resultado = validador.validar(bundle);
        List<SingleValidationMessage> erros = LocalFhirValidator.erros(resultado);
        imprimir("rac-completo COM o identificador original", resultado);

        assertThat(erros)
                .as("Procedure.identifier é 0..1 e sem restrição de formato no R4 base. Só "
                        + "reprova se o BRProcedimentoRealizado-1.0 estiver efetivamente "
                        + "aplicado e a constraint cmd-1 estiver sendo avaliada.")
                .isNotEmpty();
    }

    private static Bundle ler(String recurso) {
        try (InputStream in = CamadaQuatroRacTest.class.getClassLoader().getResourceAsStream(recurso)) {
            if (in == null) {
                throw new IllegalStateException("Artefato de referência ausente: " + recurso);
            }
            return ctx.newJsonParser().parseResource(Bundle.class, new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler " + recurso, e);
        }
    }

    private static void imprimir(String rotulo, ValidationResult resultado) {
        System.out.println("--- " + rotulo + " ---");
        resultado
                .getMessages()
                .forEach(m ->
                        System.out.printf("  [%s] %s: %s%n", m.getSeverity(), m.getLocationString(), m.getMessage()));
    }
}
