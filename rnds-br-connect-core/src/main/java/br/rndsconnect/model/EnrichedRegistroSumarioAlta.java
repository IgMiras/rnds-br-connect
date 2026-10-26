package br.rndsconnect.model;

import br.rndsconnect.model.VocabularioClinicoFhir.CategoriaAlergia;
import br.rndsconnect.model.VocabularioClinicoFhir.CriticidadeAlergia;
import br.rndsconnect.model.VocabularioClinicoFhir.SexoIndividuo;
import br.rndsconnect.model.VocabularioClinicoFhir.SituacaoClinica;
import br.rndsconnect.model.VocabularioClinicoFhir.SituacaoPrescricao;
import br.rndsconnect.model.VocabularioClinicoFhir.TipoAlergia;
import br.rndsconnect.model.VocabularioClinicoFhir.UnidadeTempo;
import br.rndsconnect.model.VocabularioClinicoFhir.VerificacaoAlergia;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * DTO enriquecido do Sumário de Alta (SA): o que a Camada 2 entrega à Camada 3.
 *
 * <p>Mesma forma de {@link br.rndsconnect.dto.RegistroSumarioAltaDTO}, com cada código local
 * já traduzido para o vocabulário da RNDS e carregando o seu {@code system}.
 *
 * <p>Desenhado a partir do artefato de referência validado ({@code golden/sa-completo.json}) e
 * dos elementos {@code MustSupport} dos perfis — não só dos obrigatórios. Cada campo opcional
 * existe porque o perfil o marca como algo que a implementação deve saber tratar.
 *
 * <p><strong>Exclusão declarada:</strong> {@code Encounter.partOf} não é exposto. Exigiria um
 * segundo {@code Encounter} completo no Bundle — o contato do qual a internação faz parte, com
 * diagnósticos e procedimentos próprios —, o que não cabe num sumário de um único contato. A
 * exclusão está registrada no {@code CoberturaMustSupportTest}.
 *
 * @author Igor Mendes Domingues Miras
 */
@Value
@Builder
public class EnrichedRegistroSumarioAlta {

    String idLocalRegistro;
    String cnsPaciente;
    String cpfPaciente;
    /** Excludente com os identificadores. */
    IndividuoNaoIdentificado individuoNaoIdentificado;
    /** CNES do estabelecimento — {@code Composition.author} e {@code Encounter.serviceProvider}. */
    String cnesEstabelecimento;

    Instant dataRegistroOrigem;
    /** {@code Composition.category} e {@code Encounter.class} — BRModalidadeAssistencial. */
    CodedValue modalidadeAssistencial;

    ContatoAssistencial contato;
    /** Principal e secundários, 1..*. O de menor {@code ordem} é o principal. */
    List<Diagnostico> diagnosticos;
    /** 1..* — seção obrigatória herdada do CMD. */
    List<Procedimento> procedimentos;
    /** {@code Condition.code.text} — opcional, texto livre por imposição do perfil. */
    String restricaoFuncional;
    /** 1..1 — o perfil admite uma única alergia por sumário. */
    Alergia alergia;
    /**
     * A prescrição de alta é um <strong>documento</strong>, não uma lista solta: o perfil exige
     * um {@code Composition} do tipo RPM envolvendo os itens. O exemplo oficial descartou esse
     * envelope (defeito nº 15), e o DTO o preserva por construção.
     */
    PrescricaoAlta prescricaoAlta;
    /** {@code ClinicalImpression.summary} — 1..1. */
    String resumoEvolucaoClinica;
    /** {@code CarePlan.description} — 1..1. */
    String planoCuidados;
    /** {@code Observation.valueString} — 1..1. */
    String informacoesAdicionais;

    /** O contato assistencial — {@code BRContatoAssistencial-1.0}, com os 30 {@code MustSupport}. */
    @Value
    @Builder
    public static class ContatoAssistencial {
        /**
         * {@code Encounter.priority} — BRCaraterAtendimento, os sete códigos numéricos da RNDS.
         *
         * <p>Contraste que vale registrar: no RIRA, o mesmo conceito usa o
         * {@code BRCaraterAtendimentoMIRA}, que compõe o {@code request-priority} do HL7. Mesmo
         * conceito, dois serviços, dois vocabulários incompatíveis.
         */
        CodedValue caraterAtendimento;
        /** {@code period.start} — admissão. */
        Instant dataInicio;
        /** {@code period.end} — alta. */
        Instant dataFim;
        /** {@code hospitalization.admitSource} — BRProcedencia. Obrigatório: 1..1 no perfil. */
        CodedValue procedencia;
        /** {@code hospitalization.dischargeDisposition} — BRMotivoDesfecho. Obrigatório: 1..1. */
        CodedValue motivoDesfecho;
        /** Profissional responsável pela alta — {@code participant.individual}. */
        String cnsProfissionalAlta;
        /** Presente ⇒ identifica a <em>lotação</em> ({@code CNS-CNES}); ausente ⇒ só o profissional. */
        String cnesLotacaoProfissionalAlta;
        /** Extensão {@code BROcupacao-1.0} do participante — BRCBO. */
        CodedValue cboProfissionalAlta;

        Integer equipe;
        /**
         * Opcional ⇒ {@code Location} com perfil {@code BRLocalAtendimento-1.0}. É um
         * <strong>tipo</strong> de local ("Unidade de Terapia Intensiva"), não um leito: o perfil
         * fixa {@code Location.mode} em {@code kind}.
         */
        String localAtendimento;
        /** Opcional ⇒ {@code Observation} com perfil {@code BRObservacaoDescritiva-1.0}. */
        String motivoAtendimento;
    }

    /** Diagnóstico — {@code BRProblemaDiagnostico}. */
    @Value
    @Builder
    public static class Diagnostico {
        CodedValue cid10;
        /** {@code Encounter.diagnosis.rank} — 1 é o principal. */
        Integer ordem;
        /** Verdadeiro ⇒ {@code diagnosis.use = AD}, presente na admissão. */
        Boolean presenteNaAdmissao;

        SituacaoClinica situacaoClinica;
        String observacao;
    }

    /** Procedimento — {@code BRProcedimentoRealizado-1.0}. */
    @Value
    @Builder
    public static class Procedimento {
        /** BRTabelaSUS, 10 dígitos. */
        CodedValue procedimento;

        Instant dataRealizacao;
        /**
         * Número de autorização. Deve casar a <strong>expressão</strong> da constraint
         * {@code cmd-1} — {@code 999999-[ME]-99-99-999999-9999999-9} —, e não o texto que a
         * mensagem de erro exibe, que a contradiz (defeito nº 16).
         */
        String numeroAutorizacao;
        /** Extensão {@code BRQuantidade-1.0} — obrigatória: {@code extension:quantity} é 1..1 no perfil. */
        Integer quantidade;
        /** Obrigatório: {@code diagnosis:procedure.condition.extension:financiamento} é 1..1. */
        CodedValue financiamento;

        CodedValue cboExecutante;
        String cnesExecutante;
        /** {@code performer.onBehalfOf} — opcional. */
        String cnesEmNomeDe;

        Integer equipe;
        String observacao;
    }

    /** Alergia ou reação adversa — {@code BRAlergiaReacaoAdversa-1.0}. */
    @Value
    @Builder
    public static class Alergia {
        /** O agente — BRMedicamento. */
        CodedValue agente;
        /** Texto livre: não passa pelo Motor Semântico. */
        String descricaoAgente;

        CategoriaAlergia categoria;
        CriticidadeAlergia criticidade;
        TipoAlergia tipo;
        VerificacaoAlergia verificacao;
        LocalDate dataInicio;
        /** {@code reaction.manifestation} — BRMedDRA. */
        CodedValue manifestacao;

        String observacao;
    }

    /** O documento de prescrição embutido — {@code BRRegistroPrescricaoMedicamento}, tipo RPM. */
    @Value
    @Builder
    public static class PrescricaoAlta {
        /** {@code MedicationRequest.recorder}. */
        String cnsPrescritor;

        Instant dataPrescricao;
        /** 1..* */
        List<ItemPrescricao> itens;
    }

    /** Um medicamento prescrito — {@code MedicationRequest} + {@code Medication}. */
    @Value
    @Builder
    public static class ItemPrescricao {
        /** BRObmCATMAT. */
        CodedValue medicamento;
        /** BRUnidadeMedida. */
        CodedValue formaFarmaceutica;

        CodedValue viaAdministracao;
        BigDecimal dose;
        CodedValue unidadeDose;
        /** {@code maxDosePerAdministration} — obrigatório no perfil. */
        BigDecimal doseMaximaPorAdministracao;
        /** {@code timing.repeat.frequency}. */
        Integer frequencia;
        /** {@code timing.repeat.count}. */
        Integer vezes;
        /** {@code timing.repeat.countMax} — obrigatório no perfil; o exemplo oficial pôs {@code true}. */
        Integer vezesMaximo;
        /** Extensão {@code BRTurno}. */
        CodedValue turno;

        Integer periodo;
        UnidadeTempo unidadePeriodo;
        /** Se necessário — {@code asNeededBoolean} (o exemplo oficial grafou {@code asNeedBoolean}). */
        Boolean seNecessario;

        String instrucaoPaciente;
        SituacaoPrescricao situacao;
        String observacao;
    }

    /** Dados demográficos observados de um indivíduo sem identificação. */
    @Value
    @Builder
    public static class IndividuoNaoIdentificado {
        SexoIndividuo sexo;
        Integer anoNascimento;
        CodedValue justificativa;
    }
}
