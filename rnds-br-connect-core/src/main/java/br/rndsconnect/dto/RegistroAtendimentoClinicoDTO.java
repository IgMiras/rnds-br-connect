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
 * Modelo canônico do Registro de Atendimento Clínico (RAC) — entrada pública do serviço, no
 * vocabulário do sistema cliente.
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
public class RegistroAtendimentoClinicoDTO {

    String idLocalRegistro;
    String cnsPaciente;
    String cpfPaciente;
    /**
     * A forma alternativa do indivíduo — excludente com o CNS e o CPF (manual, 5.5.2.4.2).
     *
     * <p>Medido em 10/10: dos quatorze contextos de {@code subject} do pacote do RAC, só quatro
     * aceitam a extensão {@code BRIndividuoNaoIdentificado-1.0} — o {@code Composition} do RAC,
     * o {@code Encounter}, a {@code Condition} de problema/diagnóstico e o {@code Procedure}. São
     * exatamente os perfis das três seções obrigatórias, e é por isso que o RAC representa um
     * indivíduo não identificado e o Sumário de Alta não (defeito nº 20).
     *
     * <p>Com esta forma o documento fica restrito: alergia, prescrição, plano de cuidados,
     * atestado e motivo de atendimento são recusados na construção, porque os perfis deles
     * exigem {@code subject.identifier} 1..1 ou não permitem a extensão. As medidas observadas
     * ficam — o {@code BRMedidaObservada} não emite {@code subject}.
     */
    IndividuoNaoIdentificadoDTO individuoNaoIdentificado;
    /** CNES do estabelecimento — {@code Composition.author} e {@code Encounter.serviceProvider}. */
    String cnesEstabelecimento;
    /** {@code Composition.date} — data do registro no sistema de origem. */
    Instant dataRegistroOrigem;
    /** {@code Composition.category} e {@code Encounter.class} — BRModalidadeAssistencial. */
    String modalidadeAssistencialLocal;

    /** 1..1 — seção {@code informacoesContatoAssistencial}, obrigatória desde o CMD. */
    ContatoAssistencialDTO contato;
    /**
     * 1..* — o RAC eleva a fatia {@code problemasDiagnosticosAvaliados} do CMD, que é opcional
     * no sumário de alta, a obrigatória. Um atendimento clínico sem problema avaliado não é
     * representável.
     */
    List<DiagnosticoDTO> diagnosticos;
    /** 1..* — seção {@code procedimentosRealizados}, obrigatória desde o CMD. */
    List<ProcedimentoDTO> procedimentos;
    /** 0..* — antropometria, sinais vitais e afins, uma {@code Observation} por medida. */
    List<MedidaObservadaDTO> medidas;
    /** 0..* — o RAC admite várias alergias, ao contrário do sumário de alta, que admite uma. */
    List<AlergiaDTO> alergias;
    /**
     * 0..1 — a prescrição é um <strong>documento</strong> embutido: o perfil exige um
     * {@code Composition} do tipo RPM envolvendo os itens, e não uma lista solta.
     */
    PrescricaoDTO prescricao;
    /** 0..1 — {@code CarePlan.description} do {@code BRPlanoCuidados-1.0}. */
    String planoCuidados;
    /** 0..* — atestado médico ou odontológico, incluído pela revisão 1.1 do manual. */
    List<AtestadoDTO> atestados;

    /** O contato assistencial — {@code BRContatoAssistencial-1.0}. */
    @Value
    @Builder
    public static class ContatoAssistencialDTO {
        /**
         * {@code Encounter.priority} — BRCaraterAtendimento.
         *
         * <p>O mesmo conceito usa o {@code BRCaraterAtendimentoMIRA}, que compõe o
         * {@code request-priority} do HL7, no RIRA. Dois serviços, dois vocabulários
         * incompatíveis para caráter de atendimento.
         */
        String caraterAtendimentoLocal;
        /** {@code period.start}. */
        Instant dataInicio;
        /** {@code period.end}. */
        Instant dataFim;
        /** {@code hospitalization.admitSource} — BRProcedencia. */
        String procedenciaLocal;
        /** {@code hospitalization.dischargeDisposition} — BRMotivoDesfecho. */
        String motivoDesfechoLocal;
        /**
         * Extensão {@code BROutrasInformacoes} em {@code hospitalization}, um
         * {@code Annotation}. O Builder carimba o {@code time} com a data do registro.
         */
        String outrasInformacoes;
        /** {@code participant.type} — BRResponsabilidadeParticipante. */
        String responsabilidadeParticipanteLocal;
        /** {@code participant.individual}. */
        String cnsProfissional;
        /** Presente ⇒ identifica a <em>lotação</em> ({@code CNS-CNES}); ausente ⇒ só o profissional. */
        String cnesLotacaoProfissional;
        /** Extensão {@code BROcupacao-1.0} do participante — BRCBO. */
        String cboProfissionalLocal;
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
    public static class DiagnosticoDTO {
        /** BRCID10 ou BRCIAP2 — o perfil admite os dois vocabulários no mesmo elemento. */
        String problemaDiagnosticoLocal;
        /** {@code Encounter.diagnosis.rank} — 1 é o principal. */
        Integer ordem;
        /** {@code Encounter.diagnosis.use} — BRPapelProblemaDiagnostico. */
        String papelLocal;

        SituacaoClinica situacaoClinica;
        String observacao;
    }

    /** Procedimento realizado — {@code BRProcedimentoRealizado-1.0}. */
    @Value
    @Builder
    public static class ProcedimentoDTO {
        /** BRTabelaSUS, 10 dígitos. */
        String procedimentoLocal;

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
        String financiamentoLocal;
        /** {@code performer.function} — BRCBO. */
        String cboExecutanteLocal;

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
    public static class MedidaObservadaDTO {
        /** {@code Observation.code} — o que foi medido. LOINC. */
        String medidaLocal;
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
        String valorCodigoLocal;
        /** {@code method} — BRPosicaoIndividuo, posição do indivíduo na aferição. */
        String metodoLocal;
        /** {@code bodySite} — BRLocalAfericao. */
        String localAfericaoLocal;
        /** Extensão {@code BROrigemMedida} — de onde veio o dado. LOINC. */
        String origemMedidaLocal;
        /** Extensão {@code BRRoupasUsadasMedicao} — vestimenta na pesagem. LOINC. */
        String roupasUsadasLocal;
        /** {@code effectiveTiming.code} — BRFrequenciaUsoSubstancia. */
        String frequenciaUsoLocal;

        String observacao;
    }

    /** Alergia ou reação adversa — {@code BRAlergiaReacaoAdversa-1.0}. */
    @Value
    @Builder
    public static class AlergiaDTO {
        /** O agente — BRAlergenos, que compõe CBARA, imunobiológico e medicamento. */
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
    public static class PrescricaoDTO {
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
        /** BRObmCATMAT. O código do exemplo oficial não existe no CodeSystem distribuído. */
        String medicamentoLocal;
        /** BRUnidadeMedida. */
        String formaFarmaceuticaLocal;

        String viaAdministracaoLocal;
        BigDecimal dose;
        String unidadeDoseLocal;
        BigDecimal doseMaximaPorAdministracao;
        /** {@code timing.repeat.frequency}. */
        Integer frequencia;
        /** {@code timing.repeat.count}. */
        Integer vezes;
        /** {@code timing.repeat.countMax}. */
        Integer vezesMaximo;
        /** Extensão {@code BRTurno}. */
        String turnoLocal;

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
    public static class AtestadoDTO {
        /** {@code activity.detail.scheduledTiming.event} — data de início do afastamento, 1..1. */
        Instant dataInicioAfastamento;
        /** {@code scheduledTiming.repeat.count} — tempo de afastamento em dias, 1..*. */
        Integer diasAfastamento;
        /** {@code activity.detail.description} — informações adicionais sobre o afastamento. */
        String informacoesAdicionais;
        /** 0..* — {@code addresses}, as condições que o atestado justifica. */
        List<CidAvaliadoDTO> diagnosticos;
    }

    /**
     * Condição avaliada referida pelo atestado — {@code BRCID10Avaliado-1.0}.
     *
     * <p>É o terceiro perfil de {@code Condition} do framework, e o que exige
     * {@code clinicalStatus}; o {@code BRCondicaoSaude} do REL o <strong>proíbe</strong>.
     */
    @Value
    @Builder
    public static class CidAvaliadoDTO {
        /** BRCID10, sem ponto decimal. */
        String cid10Local;
        /** {@code category} — BRCategoriaDiagnostico. */
        String categoriaDiagnosticoLocal;

        SituacaoClinica situacaoClinica;
        String observacao;
    }

    /**
     * Os dados observados de um indivíduo que não pôde ser identificado.
     *
     * <p>Mesma estrutura do Sumário de Alta, onde a extensão existe nos perfis mas o documento
     * nunca a alcança. Aqui ela é emissível de verdade, nos quatro contextos permitidos.
     */
    @Value
    @Builder
    public static class IndividuoNaoIdentificadoDTO {
        /** Sexo biológico observado. Vocabulário fechado: não passa pelo Motor Semântico. */
        SexoIndividuo sexo;
        /** Ano de nascimento estimado por observação direta da idade. Emitido como {@code AAAA}. */
        Integer anoNascimento;
        /** Justificativa da impossibilidade — domínio {@code JUSTIFICATIVA_NAO_IDENTIFICADO}. */
        String justificativaLocal;
    }
}
