package br.rndsconnect.dto;

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
 * Modelo canônico do Sumário de Alta (SA) — entrada pública do serviço, no vocabulário do
 * sistema cliente.
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
public class RegistroSumarioAltaDTO {

    String idLocalRegistro;
    String cnsPaciente;
    String cpfPaciente;

    /**
     * Indivíduo que não pôde ser identificado — excludente com {@code cnsPaciente} e
     * {@code cpfPaciente}.
     *
     * <p>Hipóteses legais da Port. nº 84/SAS/MS/1997 e da Port. nº 02/SAS/SGEP/MS/2012: o
     * acidentado grave, o indivíduo em sofrimento mental na via pública, o comatoso. Informa-se
     * o que se consegue observar — sexo e ano de nascimento estimado — e a razão da
     * impossibilidade. Pela RN03 do modelo de informação, esses dados viram parâmetro de busca
     * para a eventual atribuição de um CNS depois.
     */
    IndividuoNaoIdentificadoDTO individuoNaoIdentificado;

    /** CNES do estabelecimento — {@code Composition.author} e {@code Encounter.serviceProvider}. */
    String cnesEstabelecimento;

    Instant dataRegistroOrigem;
    /** {@code Composition.category} e {@code Encounter.class} — BRModalidadeAssistencial. */
    String modalidadeAssistencialLocal;

    ContatoAssistencialDTO contato;
    /** Principal e secundários, 1..*. O de menor {@code ordem} é o principal. */
    List<DiagnosticoDTO> diagnosticos;
    /** 1..* — seção obrigatória herdada do CMD. */
    List<ProcedimentoDTO> procedimentos;
    /** {@code Condition.code.text} — opcional, texto livre por imposição do perfil. */
    String restricaoFuncional;
    /** 1..1 — o perfil admite uma única alergia por sumário. */
    AlergiaDTO alergia;
    /**
     * A prescrição de alta é um <strong>documento</strong>, não uma lista solta: o perfil exige
     * um {@code Composition} do tipo RPM envolvendo os itens. O exemplo oficial descartou esse
     * envelope (defeito nº 15), e o DTO o preserva por construção.
     */
    PrescricaoAltaDTO prescricaoAlta;
    /** {@code ClinicalImpression.summary} — 1..1. */
    String resumoEvolucaoClinica;
    /** {@code CarePlan.description} — 1..1. */
    String planoCuidados;
    /** {@code Observation.valueString} — 1..1. */
    String informacoesAdicionais;

    /** O contato assistencial — {@code BRContatoAssistencial-1.0}, com os 30 {@code MustSupport}. */
    @Value
    @Builder
    public static class ContatoAssistencialDTO {
        /**
         * {@code Encounter.priority} — BRCaraterAtendimento, os sete códigos numéricos da RNDS.
         *
         * <p>Contraste que vale registrar: no RIRA, o mesmo conceito usa o
         * {@code BRCaraterAtendimentoMIRA}, que compõe o {@code request-priority} do HL7. Mesmo
         * conceito, dois serviços, dois vocabulários incompatíveis.
         */
        String caraterAtendimentoLocal;
        /** {@code period.start} — admissão. */
        Instant dataInicio;
        /** {@code period.end} — alta. */
        Instant dataFim;
        /** {@code hospitalization.admitSource} — BRProcedencia. Obrigatório: 1..1 no perfil. */
        String procedenciaLocal;
        /** {@code hospitalization.dischargeDisposition} — BRMotivoDesfecho. Obrigatório: 1..1. */
        String motivoDesfechoLocal;
        /** Profissional responsável pela alta — {@code participant.individual}. */
        String cnsProfissionalAlta;
        /** Presente ⇒ identifica a <em>lotação</em> ({@code CNS-CNES}); ausente ⇒ só o profissional. */
        String cnesLotacaoProfissionalAlta;
        /** Extensão {@code BROcupacao-1.0} do participante — BRCBO. */
        String cboProfissionalAltaLocal;
        /**
         * Identificador da equipe de saúde. O mesmo perfil marca a extensão como MustSupport no
         * participante do contato e no executante do procedimento; o RAC já a emitia, o SA não.
         */
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
    public static class DiagnosticoDTO {
        String cid10Local;
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
    public static class ProcedimentoDTO {
        /** BRTabelaSUS, 10 dígitos. */
        String procedimentoLocal;

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
        String financiamentoLocal;

        String cboExecutanteLocal;
        String cnesExecutante;
        /** {@code performer.onBehalfOf} — opcional. */
        String cnesEmNomeDe;
        /** Identificador da equipe de saúde que executou. */
        Integer equipe;

        String observacao;
    }

    /** Alergia ou reação adversa — {@code BRAlergiaReacaoAdversa-1.0}. */
    @Value
    @Builder
    public static class AlergiaDTO {
        /** O agente — BRMedicamento. */
        String agenteLocal;
        /**
         * Descrição do agente em texto livre, no {@code code.text}. O código identifica a
         * substância no vocabulário da RNDS; o texto preserva como ela foi registrada na
         * origem, que nem sempre coincide.
         */
        String descricaoAgente;

        CategoriaAlergia categoria;
        CriticidadeAlergia criticidade;
        TipoAlergia tipo;
        VerificacaoAlergia verificacao;
        LocalDate dataInicio;
        /** {@code reaction.manifestation} — BRMedDRA. */
        String manifestacaoLocal;

        String observacao;
    }

    /** O documento de prescrição embutido — {@code BRRegistroPrescricaoMedicamento}, tipo RPM. */
    @Value
    @Builder
    public static class PrescricaoAltaDTO {
        /** {@code MedicationRequest.recorder}. */
        String cnsPrescritor;

        Instant dataPrescricao;
        /** 1..* */
        List<ItemPrescricaoDTO> itens;
    }

    /** Um medicamento prescrito — {@code MedicationRequest} + {@code Medication}. */
    @Value
    @Builder
    public static class ItemPrescricaoDTO {
        /** BRObmCATMAT. */
        String medicamentoLocal;
        /** BRUnidadeMedida. */
        String formaFarmaceuticaLocal;

        String viaAdministracaoLocal;
        BigDecimal dose;
        String unidadeDoseLocal;
        /** {@code maxDosePerAdministration} — obrigatório no perfil. */
        BigDecimal doseMaximaPorAdministracao;
        /** {@code timing.repeat.frequency}. */
        Integer frequencia;
        /** {@code timing.repeat.count}. */
        Integer vezes;
        /** {@code timing.repeat.countMax} — obrigatório no perfil; o exemplo oficial pôs {@code true}. */
        Integer vezesMaximo;
        /** Extensão {@code BRTurno}. */
        String turnoLocal;

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
    public static class IndividuoNaoIdentificadoDTO {
        /** Sexo biológico observado. Vocabulário fechado: não passa pelo Motor Semântico. */
        SexoIndividuo sexo;
        /** Ano de nascimento estimado por observação direta da idade. Emitido como {@code AAAA}. */
        Integer anoNascimento;
        /** Justificativa da impossibilidade de identificação. */
        String justificativaLocal;
    }
}
