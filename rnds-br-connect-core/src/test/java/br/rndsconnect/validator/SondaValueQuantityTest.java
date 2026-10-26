package br.rndsconnect.validator;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.ValidationResult;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Specimen;
import org.hl7.fhir.r4.model.Type;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Sonda: quais formas de {@code Observation.value[x]} o perfil do REL de fato aceita.
 *
 * <p>Este não é um teste de comportamento do framework — é um <strong>experimento sobre o
 * artefato oficial</strong>. Ele não afirma nada: executa quatro variações contra a Camada 4 e
 * imprime o veredicto de cada uma.
 *
 * <p>A pergunta que responde: o perfil declara {@code Observation.value[x]} com tipos
 * {@code Quantity} e {@code CodeableConcept}, e um binding {@code required} a um ValueSet de
 * resultados <em>qualitativos</em>. No FHIR, binding sobre {@code Quantity} recai sobre
 * {@code Quantity.code} — a unidade. Isso torna a combinação "quantitativo + UCUM" impossível
 * de satisfazer, a menos que a unidade seja omitida.
 *
 * @author Igor Mendes Domingues Miras
 */
class SondaValueQuantityTest {

    private static final String SD = "http://www.saude.gov.br/fhir/r4/StructureDefinition/";
    private static final String CS = "http://www.saude.gov.br/fhir/r4/CodeSystem/";
    private static final String UCUM = "http://unitsofmeasure.org";
    private static final Date QUANDO = Date.from(java.time.Instant.parse("2024-04-01T11:18:30Z"));

    private static LocalFhirValidator validador;

    @BeforeAll
    static void carregar() {
        FhirContext ctx = FhirContext.forR4();
        validador = new LocalFhirValidator(ctx, ConformancePackage.paraServico(ctx, Servico.REL, "conformance"));
    }

    @Test
    @DisplayName("SONDA: quais formas de value[x] o BRDiagnosticoLaboratorioClinico-3.2.1 aceita")
    void sondarFormasDeValue() {
        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  SONDA — Observation.value[x] no perfil do REL v3.2.1              ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════╝");

        sondar(
                "A) CodeableConcept (qualitativo)",
                new CodeableConcept()
                        .addCoding(new org.hl7.fhir.r4.model.Coding()
                                .setSystem(CS + "BRTipoResultadoDTNT")
                                .setCode("2")));

        sondar(
                "B) Quantity com system+code UCUM",
                new Quantity()
                        .setValue(new BigDecimal("4.7"))
                        .setSystem(UCUM)
                        .setCode("{log10}/mL")
                        .setUnit("{log10}/mL"));

        sondar(
                "C) Quantity com unit textual, SEM system/code",
                new Quantity().setValue(new BigDecimal("4.7")).setUnit("log10 cópias/mL"));

        sondar("D) Quantity apenas com value", new Quantity().setValue(new BigDecimal("4.7")));

        System.out.println("──────────────────────────────────────────────────────────────────────");
    }

    private static void sondar(String rotulo, Type valor) {
        ValidationResult r = validador.validar(montar(valor));
        List<ca.uhn.fhir.validation.SingleValidationMessage> erros = LocalFhirValidator.erros(r);
        System.out.printf("%n%-42s -> %s%n", rotulo, erros.isEmpty() ? "APROVADO" : "REPROVADO");
        erros.forEach(m -> {
            if (m.getLocationString().contains("value")) {
                System.out.println("      " + m.getMessage());
            }
        });
    }

    /** Bundle REL mínimo, variando apenas {@code Observation.value[x]}. */
    private static Bundle montar(Type valor) {
        String uComp = "aa000000-0000-4000-8000-000000000001";
        String uCond = "aa000000-0000-4000-8000-000000000002";
        String uObs = "aa000000-0000-4000-8000-000000000003";
        String uSpec = "aa000000-0000-4000-8000-000000000004";

        Composition c = new Composition();
        c.setMeta(new Meta().addProfile(SD + "BRResultadoExameLaboratorial-3.2.1"));
        c.setStatus(Composition.CompositionStatus.FINAL);
        c.setType(cc(CS + "BRTipoDocumento", "REL"));
        c.setSubject(refId(SD + "BRIndividuo-1.0", "898005900000003"));
        c.setDate(QUANDO);
        c.addAuthor(refId(SD + "BRPessoaJuridicaProfissionalLiberal-1.0", "00394544000185"));
        c.setTitle("Resultado de Exame Laboratorial");
        Composition.SectionComponent s = c.addSection();
        s.addEntry(new Reference("urn:uuid:" + uCond));
        s.addEntry(new Reference("urn:uuid:" + uObs));

        Condition cond = new Condition();
        cond.setMeta(new Meta().addProfile(SD + "BRCondicaoSaude"));
        cond.setCode(cc(CS + "BRCID10", "B04"));
        cond.setSubject(refId(SD + "BRIndividuo-1.0", "898005900000003"));
        cond.addStage().addAssessment(new Reference("urn:uuid:" + uObs));

        Observation o = new Observation();
        o.setMeta(new Meta().addProfile(SD + "BRDiagnosticoLaboratorioClinico-3.2.1"));
        o.setStatus(Observation.ObservationStatus.FINAL);
        o.addCategory(cc(CS + "BRSubgrupoTabelaSUS", "0202"));
        o.setCode(cc(CS + "BRNomeExameLOINC", "94309-2"));
        o.setSubject(refId(SD + "BRIndividuo-1.0", "898005900000003"));
        o.setEffective(new DateTimeType(QUANDO));
        o.setIssuedElement(new InstantType(QUANDO));
        o.addPerformer(refId(SD + "BREstabelecimentoSaude-1.0", "0123456"));
        o.addPerformer(refId(SD + "BRPessoaJuridicaProfissionalLiberal-1.0", "00394544000185"));
        o.setValue(valor);
        o.setMethod(new CodeableConcept().setText("RT-PCR em tempo real."));
        o.addReferenceRange().setText("Não detectável em não infectados.");
        o.setSpecimen(new Reference("urn:uuid:" + uSpec));

        Specimen sp = new Specimen();
        sp.setMeta(new Meta().addProfile(SD + "BRAmostraBiologica-1.0"));
        sp.setType(cc(CS + "BRTipoAmostraGAL", "SWNAFA"));

        Bundle b = new Bundle();
        b.setMeta(new Meta().setLastUpdated(QUANDO));
        b.setIdentifier(new Identifier()
                .setSystem("http://www.saude.gov.br/fhir/r4/NamingSystem/BRRNDS-29376")
                .setValue("SONDA-001"));
        b.setType(Bundle.BundleType.DOCUMENT);
        b.setTimestamp(QUANDO);
        b.addEntry().setFullUrl("urn:uuid:" + uComp).setResource(c);
        b.addEntry().setFullUrl("urn:uuid:" + uCond).setResource(cond);
        b.addEntry().setFullUrl("urn:uuid:" + uObs).setResource(o);
        b.addEntry().setFullUrl("urn:uuid:" + uSpec).setResource(sp);
        return b;
    }

    private static CodeableConcept cc(String system, String code) {
        CodeableConcept x = new CodeableConcept();
        x.addCoding().setSystem(system).setCode(code);
        return x;
    }

    private static Reference refId(String system, String value) {
        return new Reference().setIdentifier(new Identifier().setSystem(system).setValue(value));
    }
}
