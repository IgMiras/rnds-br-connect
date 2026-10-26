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
 * DTO enriquecido do Registro de Atendimento Clínico (RAC): o que a Camada 2 entrega à Camada 3.
 *
 * <p>Mesma forma de {@link br.rndsconnect.dto.RegistroAtendimentoClinicoDTO}, com cada código
 * local já traduzido para o vocabulário da RNDS e carregando o seu {@code system}.
 *
 * <p>Desenhado a partir do artefato de referência ({@code golden/rac-completo.json}, aprovado
 * pela Camada 4 e pelo validador desktop do DATASUS) e da <strong>cardinalidade mínima da
 * cadeia de perfis</strong> — o {@code BRRegistroAtendimentoClinico} sobre o
 * {@code BRConjuntoMinimoDados-1.1} —, não do exemplo oficial. Os opcionais existem porque o
 * perfil os marca como {@code MustSupport}: 103 elementos nos 14 perfis instanciados, dos quais
 * 85 já são emitidos pelo SA, 11 pelo RIA-R e pelo RIRA, e 7 são do próprio perfil do
 * documento — herdados do {@code BRConjuntoMinimoDados-1.1} e invisíveis no
 * {@code differential}, que a auditoria deixou de ler em 13/10 (Arquitetura §12.3).
 *
 * <p><strong>Pacote vigente:</strong> {@code conformance/rac}, originado do
 * {@code arquivos_definicoes_SA}, com o {@code BRRegistroAtendimentoClinico} na versão 2.1. A
 * escolha foi por medição e não por rótulo: a versão 3.0, distribuída apenas no pacote
 * completo, discrimina a fatia {@code cadernetaCrianca} por um artefato que esse mesmo pacote
 * não traz, e com isso <em>nenhuma</em> seção do documento chega a ser avaliada (ADR-09).
 *
 * <p><strong>Três exclusões declaradas:</strong>
 *
 * <ul>
 *   <li><em>Teleconsulta.</em> O manual v2.0 (§8.2) acrescenta {@code Encounter.type} com o
 *       CodeSystem {@code BRModalidadeTelessaude}. Em todos os cinco pacotes do DATASUS o
 *       elemento é {@code max=0}, e o CodeSystem não existe em nenhum deles — o exemplo oficial
 *       de teleconsulta reprova no validador do próprio DATASUS. Quando o perfil atualizado for
 *       publicado, é um campo aqui e a troca do pacote do RAC.
 *   <li><em>Marcos de desenvolvimento.</em> Fatia {@code marcosDesenvolvimento}, 0..1, cujo alvo
 *       é um {@code QuestionnaireResponse} — outro modelo de informação, fora do escopo.
 *   <li><em>Informações OPM.</em> Fatia herdada do CMD que o RAC não instancia; entra no pacote
 *       apenas pela estrutura, sem vocabulários.
 * </ul>
 *
 * @author Igor Mendes Domingues Miras
 */
@Value
@Builder
public class EnrichedRegistroAtendimentoClinico {

    String idLocalRegistro;
    String cnsPaciente;
    String cpfPaciente;
    /** Excludente com os identificadores — ver o Javadoc do DTO. */
    IndividuoNaoIdentificado individuoNaoIdentificado;
    /** CNES do estabelecimento — {@code Composition.author} e {@code Encounter.serviceProvider}. */
    String cnesEstabelecimento;
    /** {@code Composition.date} — data do registro no sistema de origem. */
    Instant dataRegistroOrigem;
    /** {@code Composition.category} e {@code Encounter.class} — BRModalidadeAssistencial. */
    CodedValue modalidadeAssistencial;

    /** 1..1 — seção {@code informacoesContatoAssistencial}, obrigatória desde o CMD. */
    ContatoAssistencial contato;
    /**
     * 1..* — o RAC eleva a fatia {@code problemasDiagnosticosAvaliados} do CMD, que é opcional
     * no sumário de alta, a obrigatória. Um atendimento clínico sem problema avaliado não é
     * representável.
     */
    List<Diagnostico> diagnosticos;
    /** 1..* — seção {@code procedimentosRealizados}, obrigatória desde o CMD. */
    List<Procedimento> procedimentos;
    /** 0..* — antropometria, sinais vitais e afins, uma {@code Observation} por medida. */
    List<MedidaObservada> medidas;
    /** 0..* — o RAC admite várias alergias, ao contrário do sumário de alta, que admite uma. */
    List<Alergia> alergias;
    /**
     * 0..1 — a prescrição é um <strong>documento</strong> embutido: o perfil exige um
     * {@code Composition} do tipo RPM envolvendo os itens, e não uma lista solta.
     */
    Prescricao prescricao;
    /** 0..1 — {@code CarePlan.description} do {@code BRPlanoCuidados-1.0}. */
    String planoCuidados;
    /** 0..* — atestado médico ou odontológico, incluído pela revisão 1.1 do manual. */
    List<Atestado> atestados;

    /** O contato assistencial — {@code BRContatoAssistencial-1.0}. */
    @Value
    @Builder
    public static class ContatoAssistencial {
        /**
         * {@code Encounter.priority} — BRCaraterAtendimento.
         *
         * <p>O mesmo conceito usa o {@code BRCaraterAtendimentoMIRA}, que compõe o
         * {@code request-priority} do HL7, no RIRA. Dois serviços, dois vocabulários
         * incompatíveis para caráter de atendimento.
         */
        CodedValue caraterAtendimento;
        /** {@code period.start}. */
        Instant dataInicio;
        /** {@code period.end}. */
        Instant dataFim;
        /** {@code hospitalization.admitSource} — BRProcedencia. */
        CodedValue procedencia;
        /** {@code hospitalization.dischargeDisposition} — BRMotivoDesfecho. */
        CodedValue motivoDesfecho;
        /**
         * Extensão {@code BROutrasInformacoes} em {@code hospitalization}, um
         * {@code Annotation}. O Builder carimba o {@code time} com a data do registro.
         */
        String outrasInformacoes;
        /** {@code participant.type} — BRResponsabilidadeParticipante. */
        CodedValue responsabilidadeParticipante;
        /** {@code participant.individual}. */
        String cnsProfissional;
        /** Presente ⇒ identifica a <em>lotação</em> ({@code CNS-CNES}); ausente ⇒ só o profissional. */
        String cnesLotacaoProfissional;
        /** Extensão {@code BROcupacao-1.0} do participante — BRCBO. */
        CodedValue cboProfissional;
        /** Extensão {@code BRIdentificacaoEquipe-1.0} — identificador da equipe, um inteiro. */
        Integer equipe;
        /**
         * Opcional ⇒ {@code Location} com perfil {@code BRLocalAtendimento-1.0}. É um
         * <strong>tipo</strong> de local, não um leito: o perfil fixa {@code Location.mode} em
         * {@code kind}.
         */
        String localAtendimento;
        /** Opcional ⇒ {@code Observation} com perfil {@code BRObservacaoDescritiva-1.0}. */
        String motivoAtendimento;
    }

    /** Problema ou diagnóstico avaliado — {@code BRProblemaDiagnostico}. */
    @Value
    @Builder
    public static class Diagnostico {
        /** BRCID10 ou BRCIAP2 — o perfil admite os dois vocabulários no mesmo elemento. */
        CodedValue problemaDiagnostico;
        /** {@code Encounter.diagnosis.rank} — 1 é o principal. */
        Integer ordem;
        /** {@code Encounter.diagnosis.use} — BRPapelProblemaDiagnostico. */
        CodedValue papel;

        SituacaoClinica situacaoClinica;
        String observacao;
    }

    /** Procedimento realizado — {@code BRProcedimentoRealizado-1.0}. */
    @Value
    @Builder
    public static class Procedimento {
        /** BRTabelaSUS, 10 dígitos. */
        CodedValue procedimento;

        Instant dataRealizacao;
        /**
         * Número de autorização. Deve casar a <strong>expressão</strong> da constraint
         * {@code cmd-1} — seis dígitos, {@code M} ou {@code E}, 2, 2, 6, 7 e 1 —, e não o texto
         * que a mensagem de erro exibe, que a contradiz (defeito nº 16). O exemplo oficial do
         * RAC reprova justamente aqui.
         */
        String numeroAutorizacao;
        /** Extensão {@code BRQuantidade-1.0}. */
        Integer quantidade;
        /** Extensão {@code BRFinanciamento-1.0} em {@code Encounter.diagnosis:procedure}. */
        CodedValue financiamento;
        /** {@code performer.function} — BRCBO. */
        CodedValue cboExecutante;

        String cnsExecutante;
        String cnesLotacaoExecutante;
        /** {@code performer.onBehalfOf}. */
        String cnesEmNomeDe;
        /** Extensão {@code BRIdentificacaoEquipe-1.0} do executante. */
        Integer equipe;

        String observacao;
    }

    /**
     * Uma medida observada — {@code BRMedidaObservada}, uma {@code Observation} por medida.
     *
     * <p>Lista genérica, e não campos tipados por medida (ADR-05): o que distingue peso de
     * pressão arterial é o código LOINC, e a tabela de códigos é problema clínico do
     * integrador. O framework dá a estrutura e valida o par ({@code system}, {@code code}); o
     * catálogo dos códigos aceitos cabe a um starter, como no REL.
     *
     * <p>O perfil admite três formas de valor e nenhuma é obrigatória: quantidade, data ou
     * código. Informe exatamente uma.
     */
    @Value
    @Builder
    public static class MedidaObservada {
        /** {@code Observation.code} — o que foi medido. LOINC. */
        CodedValue medida;
        /** {@code valueQuantity.value}. */
        BigDecimal valor;
        /**
         * {@code valueQuantity.code} — unidade UCUM.
         *
         * <p>O exemplo oficial declara o {@code system} do UCUM como uma página HTML do HL7, o
         * que o FHIR proíbe e o validador do DATASUS deixa passar. O Builder emite o canônico
         * {@code http://unitsofmeasure.org}.
         */
        String unidadeUcum;
        /** {@code valueDateTime} — para medidas que são datas, como a última menstruação. */
        Instant valorData;
        /**
         * {@code valueCodeableConcept} — para medidas cujo valor é um código.
         *
         * <p>O vocabulário varia com a medida: tipo de aleitamento, substância em uso e outros
         * têm CodeSystems distintos. A resolução do domínio a partir da medida é trabalho do
         * Motor Semântico, não deste DTO.
         */
        CodedValue valorCodigo;
        /** {@code method} — BRPosicaoIndividuo, posição do indivíduo na aferição. */
        CodedValue metodo;
        /** {@code bodySite} — BRLocalAfericao. */
        CodedValue localAfericao;
        /** Extensão {@code BROrigemMedida} — de onde veio o dado. LOINC. */
        CodedValue origemMedida;
        /** Extensão {@code BRRoupasUsadasMedicao} — vestimenta na pesagem. LOINC. */
        CodedValue roupasUsadas;
        /** {@code effectiveTiming.code} — BRFrequenciaUsoSubstancia. */
        CodedValue frequenciaUso;

        String observacao;
    }

    /** Alergia ou reação adversa — {@code BRAlergiaReacaoAdversa-1.0}. */
    @Value
    @Builder
    public static class Alergia {
        /** O agente — BRAlergenos, que compõe CBARA, imunobiológico e medicamento. */
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
    public static class Prescricao {
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
        /** BRObmCATMAT. O código do exemplo oficial não existe no CodeSystem distribuído. */
        CodedValue medicamento;
        /** BRUnidadeMedida. */
        CodedValue formaFarmaceutica;

        CodedValue viaAdministracao;
        BigDecimal dose;
        CodedValue unidadeDose;
        BigDecimal doseMaximaPorAdministracao;
        /** {@code timing.repeat.frequency}. */
        Integer frequencia;
        /** {@code timing.repeat.count}. */
        Integer vezes;
        /** {@code timing.repeat.countMax}. */
        Integer vezesMaximo;
        /** Extensão {@code BRTurno}. */
        CodedValue turno;

        Integer periodo;
        UnidadeTempo unidadePeriodo;
        /** Se necessário — {@code asNeededBoolean}. */
        Boolean seNecessario;

        String instrucaoPaciente;
        SituacaoPrescricao situacao;
        String observacao;
    }

    /**
     * Atestado médico ou odontológico — {@code BRAtestado}, um {@code CarePlan}.
     *
     * <p>Dois bindings {@code required} do perfil apontam para ValueSets de <strong>um único
     * código</strong>: o tipo do atestado só admite {@code ATM} e o estado do afastamento só
     * admite {@code unknown}. Não são domínios terminológicos, são constantes — o Builder as
     * emite e o DTO não as expõe.
     *
     * <p>{@code CarePlan.description} é {@code max=0} no perfil: o texto livre do atestado vai
     * em {@code activity.detail.description}.
     */
    @Value
    @Builder
    public static class Atestado {
        /** {@code activity.detail.scheduledTiming.event} — data de início do afastamento, 1..1. */
        Instant dataInicioAfastamento;
        /** {@code scheduledTiming.repeat.count} — tempo de afastamento em dias, 1..*. */
        Integer diasAfastamento;
        /** {@code activity.detail.description} — informações adicionais sobre o afastamento. */
        String informacoesAdicionais;
        /** 0..* — {@code addresses}, as condições que o atestado justifica. */
        List<CidAvaliado> diagnosticos;
    }

    /**
     * Condição avaliada referida pelo atestado — {@code BRCID10Avaliado-1.0}.
     *
     * <p>É o terceiro perfil de {@code Condition} do framework, e o que exige
     * {@code clinicalStatus}; o {@code BRCondicaoSaude} do REL o <strong>proíbe</strong>.
     */
    @Value
    @Builder
    public static class CidAvaliado {
        /** BRCID10, sem ponto decimal. */
        CodedValue cid10;
        /** {@code category} — BRCategoriaDiagnostico. */
        CodedValue categoriaDiagnostico;

        SituacaoClinica situacaoClinica;
        String observacao;
    }

    /** Os dados observados do indivíduo não identificado, com a justificativa já traduzida. */
    @Value
    @Builder
    public static class IndividuoNaoIdentificado {
        SexoIndividuo sexo;
        Integer anoNascimento;
        CodedValue justificativa;
    }
}
