package br.rndsconnect.validator;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.ValidationResult;
import java.util.List;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.instance.model.api.IBaseResource;

/**
 * Camada 4 — validação de conformidade local, aplicando Fail-Fast.
 *
 * <p>Executa em processo, a cada envio, o que o validador desktop do DATASUS executa
 * manualmente: mesma base de artefatos, mesmo motor de validação, dentro do ciclo de vida
 * da integração.
 *
 * <p><strong>A cadeia tem cinco elos, e nenhum é decorativo:</strong>
 *
 * <ol>
 *   <li>{@code DefaultProfileValidationSupport} — resolve os resources base do FHIR R4 dos
 *       quais derivam os perfis da geração {@code BR*} da RNDS.
 *   <li>{@code PrePopulatedValidationSupport} — os artefatos da RNDS, na forma avulsa em
 *       que o DATASUS os distribui (ADR-03).
 *   <li>{@code SnapshotGeneratingValidationSupport} — <strong>obrigatório.</strong> Os
 *       StructureDefinition da RNDS contêm apenas {@code differential}. Sem este elo o HAPI
 *       não monta o snapshot, não aplica as restrições do perfil e aprova Bundles que a
 *       RNDS recusa. É a diferença entre validar e parecer validar.
 *   <li>{@code InMemoryTerminologyServerValidationSupport} — expande os ValueSets avulsos;
 *       sem ele os bindings {@code required} não são efetivamente conferidos.
 *   <li>{@code CommonCodeSystemsTerminologyService} — cobre os CodeSystems do HL7 que os
 *       ValueSets da RNDS compõem e que não estão nos arquivos do DATASUS:
 *       {@code composition-status}, {@code event-status} e {@code condition-clinical}.
 * </ol>
 *
 * @author Igor Mendes Domingues Miras
 */
public class LocalFhirValidator {

    private final FhirValidator validator;
    private final ConformancePackage pacote;

    /**
     * @param ctx contexto FHIR R4 (reutilizável e caro de criar — mantenha um por aplicação)
     * @param pacote artefatos de conformidade já carregados
     */
    public LocalFhirValidator(FhirContext ctx, ConformancePackage pacote) {
        this.pacote = pacote;

        PrePopulatedValidationSupport rnds = new PrePopulatedValidationSupport(ctx);
        pacote.structureDefinitions().forEach(rnds::addStructureDefinition);
        pacote.valueSets().forEach(rnds::addValueSet);
        pacote.codeSystems().forEach(rnds::addCodeSystem);

        ValidationSupportChain chain = new ValidationSupportChain(
                new DefaultProfileValidationSupport(ctx),
                rnds,
                new SnapshotGeneratingValidationSupport(ctx),
                new InMemoryTerminologyServerValidationSupport(ctx),
                new CommonCodeSystemsTerminologyService(ctx));

        // Checagem de terminologia LIGADA: é ela que reprova um código fora do
        // subconjunto de um binding required. Desligá-la esvazia a camada.
        FhirInstanceValidator instanceValidator = new FhirInstanceValidator(chain);
        instanceValidator.setNoTerminologyChecks(false);

        // Nota: Practitioner/{CNS} é referência externa legítima — o profissional não é
        // resource do Bundle em nenhum dos três serviços. O HAPI emite AVISO de referência
        // não resolvida, e o contrato desta classe só reprova ERROR e FATAL.

        this.validator = ctx.newValidator();
        this.validator.registerValidatorModule(instanceValidator);
    }

    /**
     * Valida o recurso e interrompe o fluxo se ele não for conforme.
     *
     * @param recurso Bundle (ou qualquer resource) a validar
     * @throws FhirConformanceException se houver mensagem de severidade ERROR ou FATAL
     */
    public void validate(IBaseResource recurso) {
        ValidationResult resultado = validar(recurso);
        if (!resultado.isSuccessful()) {
            throw new FhirConformanceException(resultado.getMessages());
        }
    }

    /**
     * Valida sem lançar exceção, devolvendo o resultado bruto.
     *
     * <p>Usado pela suíte de testes e, quando a Camada 5 existir, pelo coletor de evidências de
     * homologação, que precisa do {@code ValidationResult} inteiro — inclusive dos avisos.
     *
     * @param recurso Bundle (ou qualquer resource) a validar
     * @return resultado completo da validação
     */
    public ValidationResult validar(IBaseResource recurso) {
        return validator.validateWithResult(recurso);
    }

    /**
     * @param resultado resultado de uma validação
     * @return apenas as mensagens de severidade ERROR ou FATAL
     */
    public static List<ca.uhn.fhir.validation.SingleValidationMessage> erros(ValidationResult resultado) {
        return resultado.getMessages().stream()
                .filter(m -> m.getSeverity() == ResultSeverityEnum.ERROR || m.getSeverity() == ResultSeverityEnum.FATAL)
                .toList();
    }

    /** @return o pacote de conformidade usado por este validador */
    public ConformancePackage getPacote() {
        return pacote;
    }
}
