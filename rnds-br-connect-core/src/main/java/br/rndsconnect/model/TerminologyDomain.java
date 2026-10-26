package br.rndsconnect.model;

/**
 * Vocabulários terminológicos que o framework traduz.
 *
 * <p>Cada constante é um ponto de extensão: o integrador registra uma
 * {@code SemanticMappingStrategy} por domínio, e o framework recusa qualquer envio cujo
 * domínio exigido não tenha estratégia registrada.
 *
 * <p>Raça/cor não figura aqui. Os documentos do escopo identificam o indivíduo apenas por
 * {@code identifier} (CNS ou CPF), e nenhum dos Bundles carrega um resource {@code Patient}.
 * O CodeSystem {@code BRRacaCor} existe na terminologia da RNDS, mas só tem destino em perfis
 * que instanciam {@code BRIndividuo-1.0}, fora do escopo implementado.
 *
 * @author Igor Mendes Domingues Miras
 */
public enum TerminologyDomain {

    // ── Serviço RIA-R ────────────────────────────────────────────────
    /** {@code "105"} → {@code "87"} (BRImunobiologico). */
    VACINA,
    /** {@code "BUTANTAN"} → {@code "152"} (BRFabricantePNI). */
    FABRICANTE,
    /** {@code "D1"} → {@code "1"} (BRDose — "1ª Dose"). */
    DOSE,
    /** {@code "IM"} → {@code "10890"} (BRViaAdministracao — Intramuscular). */
    VIA_ADMINISTRACAO,
    /** {@code "braco_dir"} → {@code "1"} (BRLocalAplicacao — Deltóide Direito). */
    LOCAL_APLICACAO,
    /** {@code "ROTINA"} → {@code "1"} (BREstrategiaVacinacao; Especial = {@code "2"}). */
    ESTRATEGIA_VACINACAO,
    /** transcrição → {@code "01"} (BRRegistroOrigem; exterior = {@code "02"}). */
    REGISTRO_ORIGEM,
    /** CBO do profissional <strong>prescritor</strong> (BRCBO). */
    OCUPACAO_CBO,
    /** motivo de indicação (BRCID10 — sem ponto: {@code "B342"}). */
    CID10,
    /** país de origem do registro transcrito (BRPais). */
    PAIS,

    // ── Compartilhados — RIA-R e RIA-C ───────────────────────────────
    /** {@code "GESTANTE"} → {@code "001801"} (BRGrupoAtendimento). */
    GRUPO_ATENDIMENTO,
    /** {@code "GESTANTE"} → {@code "2"} (BRCondicaoMaternal; {@code "1"} = Não se aplica). */
    CONDICAO_MATERNAL,

    // ── Serviço REL ──────────────────────────────────────────────────
    /** LOINC <strong>ou</strong> GAL — o {@code system} faz parte do alvo. */
    NOME_EXAME,
    /** {@code "LAB_CLINICO"} → {@code "0202"} (BRSubgrupoTabelaSUS). */
    SUBGRUPO_TABELA_SUS,
    /** HL7 v2-0487 <strong>ou</strong> BRTipoAmostraGAL. */
    TIPO_AMOSTRA,
    /** {@code "COVID"} → {@code "B342"} (BRCID10, via BRTerminologiaSuspeitaDiagnostica). */
    SUSPEITA_DIAGNOSTICA,
    /** BRResultadoQualitativoExame-2.0 — CodeSystem base + 8 {@code BRTipoResultado*}. */
    RESULTADO_QUALITATIVO,
    /** ValueSet DISTINTO: BRResultadoQualitativoExame-1.0, apenas 3 códigos. */
    INTERPRETACAO_RESULTADO,
    /** extensão BRPatogeno (BRTerminologiaPatogeno). */
    PATOGENO,

    // ── Serviço RIRA ─────────────────────────────────────────────────
    /**
     * {@code "AMBULATORIAL"} → {@code "09"} (BRModalidadeAssistencial).
     *
     * <p>O ValueSet {@code BRModalidadeAssistencialMIRA} enumera apenas dois códigos do
     * CodeSystem: {@code "04"} e {@code "09"}.
     */
    MODALIDADE_ASSISTENCIAL,

    /**
     * {@code "ELETIVO"} → {@code "routine"}.
     *
     * <p><strong>Não é o {@code BRCaraterAtendimento} da RNDS.</strong> O ValueSet
     * {@code BRCaraterAtendimentoMIRA} compõe o {@code request-priority} do HL7, e admite
     * apenas {@code routine} e {@code urgent}. Os sete códigos numéricos do CodeSystem
     * brasileiro — de {@code "01"} Eletivo a {@code "99"} — reprovam o binding.
     */
    CARATER_ATENDIMENTO,

    /**
     * {@code "CONSULTA_CARDIO"} → {@code "0406010013"}.
     *
     * <p>Binding a {@code BRProcedimentosNacionais-1.0}, que compõe <strong>dois</strong>
     * CodeSystems com estruturas incompatíveis: {@code BRTabelaSUS} (4.932 códigos de
     * 10 dígitos, cujos dois primeiros são o grupo) e {@code BRCBHPMTUSS} (32.891 códigos de
     * 3 dígitos). O {@code system} é estrutural — ver {@code RN03ProcedimentoExigeCbo}.
     */
    PROCEDIMENTO,

    // ── Serviço REDFM ────────────────────────────────────────────────
    /**
     * {@code "AMOXICILINA_500"} → {@code "@brasil12898180271"}.
     *
     * <p>Vocabulário de Medicamentos Padronizado. Os códigos têm a forma
     * {@code @brasilNNNNNNNNNNN} — diferente de tudo que os outros serviços usam, e o único
     * no framework que não é numérico nem alfanumérico curto.
     */
    MEDICAMENTO,

    /** {@code "COMPRIMIDO"} → {@code "13"} (BRUnidadeMedida) — forma farmacêutica. */
    FORMA_FARMACEUTICA,

    /**
     * {@code "AMBULATORIAL"} → {@code "community"}.
     *
     * <p>Ambiente da dispensação. O binding é ao {@code medicationdispense-category} do HL7,
     * e não a um CodeSystem da RNDS — mesmo padrão do {@code CARATER_ATENDIMENTO} no RIRA.
     */
    CATEGORIA_DISPENSACAO,

    /** {@code "TOTAL"} → {@code "fully-dispensing"} (BRTipoDispensacaoRealizada). */
    TIPO_DISPENSACAO,

    /** {@code "COMPRIMIDO"} → {@code "13"} — unidade da dose prescrita. */
    UNIDADE_DOSE,

    // ── Serviço SA ───────────────────────────────────────────────────
    /**
     * {@code "URGENCIA"} → {@code "02"} (BRCaraterAtendimento, os sete códigos numéricos).
     *
     * <p><strong>Não é o mesmo domínio que {@link #CARATER_ATENDIMENTO}.</strong> O conceito é
     * idêntico — caráter do atendimento —, mas o RIRA o vincula ao {@code request-priority} do
     * HL7 ({@code routine}, {@code urgent}) e o SA ao {@code BRCaraterAtendimento} da RNDS. Uma
     * única tabela não serve aos dois. Daí a regra que o SA tornou explícita: <strong>o domínio
     * terminológico é definido pelo vocabulário de destino, e não pelo conceito clínico</strong>.
     */
    CARATER_ATENDIMENTO_RNDS,

    /** {@code "DEMANDA_ESPONTANEA"} → {@code "09"} (BRProcedencia) — de onde o paciente veio. */
    PROCEDENCIA,

    /** {@code "ENCAMINHAMENTO"} → {@code "03"} (BRMotivoDesfecho) — como o contato terminou. */
    MOTIVO_DESFECHO,

    /** {@code "PLANO_PUBLICO"} → {@code "02"} (BRFinanciamento) — obrigatório em cada procedimento. */
    FINANCIAMENTO,

    /** {@code "DIPIRONA_SOLUCAO"} → {@code "BR0267205U0063"} (BRMedicamento) — o agente da alergia. */
    ALERGENO,

    /** {@code "ANAFILAXIA"} → {@code "10002198"} (BRMedDRA) — a manifestação da reação. */
    MANIFESTACAO_REACAO,

    /**
     * {@code "PARACETAMOL_500"} → {@code "BR0267778"} (BRObmCATMAT).
     *
     * <p>Segundo caso da mesma regra: o REDFM identifica medicamentos pelo {@code BRObmVMP}
     * ({@link #MEDICAMENTO}), o SA pelo {@code BRObmCATMAT}. E a alergia, no mesmo documento,
     * usa um terceiro vocabulário — o {@code BRMedicamento} ({@link #ALERGENO}). Três
     * vocabulários para medicamento, e nenhum informa o princípio ativo.
     */
    MEDICAMENTO_CATMAT,

    /** {@code "NOITE"} → {@code "3"} (BRTurno) — extensão do horário de administração. */
    TURNO,

    // ── Serviço RAC ──────────────────────────────────────────────────
    /**
     * {@code "SOLICITANTE"} → {@code "solicitante"} (BRResponsabilidadeParticipante).
     *
     * <p>Papel do profissional <strong>no contato</strong>, não a sua ocupação: o CBO vai em
     * paralelo, na extensão {@code BROcupacao}. O SA fixa {@code "alta"} no Builder porque o
     * documento só admite um papel; o RAC o expõe, porque um atendimento clínico tem
     * solicitante, autorizador e responsável pelo atendimento.
     */
    RESPONSABILIDADE_PARTICIPANTE,

    /**
     * {@code "VARIZES_ESOFAGO"} → {@code "I859"} (BRCID10) <strong>ou</strong> {@code "A03"}
     * (BRCIAP2).
     *
     * <p>Não é o {@link #CID10}. O {@code BRProblemaDiagnostico} compõe dois CodeSystems, e a
     * atenção primária costuma codificar em CIAP-2 o que a especializada codifica em CID-10.
     * O {@code system} faz parte do alvo.
     */
    PROBLEMA_DIAGNOSTICO,

    /** {@code "NAO_PRESENTE_ADMISSAO"} → {@code "NAD"} (BRPapelProblemaDiagnostico, ou AD do HL7). */
    PAPEL_PROBLEMA_DIAGNOSTICO,

    /** {@code "PRINCIPAL"} → {@code "01"} (BRCategoriaDiagnostico) — categoria do CID do atestado. */
    CATEGORIA_DIAGNOSTICO,

    /**
     * {@code "PESO"} → {@code "29463-7"} (LOINC) — o que foi medido.
     *
     * <p>O ValueSet {@code BRTipoObservacao-1.0} compõe três CodeSystems: treze códigos LOINC,
     * o {@code BRTipoObservacao} da RNDS (que tem {@code "P"} para peso e {@code "A"} para
     * altura) e um código da Tabela SUS. A mesma medida tem, portanto, duas grafias oficiais —
     * e o artefato de referência usa a LOINC.
     *
     * <p>É aqui que o ADR-05 pesa mais: antropometria e sinal vital são LOINC, e a tabela é
     * problema clínico do integrador.
     */
    TIPO_OBSERVACAO,

    /**
     * {@code "ALCOOL"} → {@code "alcool"} (BRTipoSubstanciaUso) ou {@code "predominante"}
     * (BRTipoAleitamentoMaterno).
     *
     * <p>O único domínio do framework <strong>sem binding no perfil</strong>: o
     * {@code Observation.value[x]} do {@code BRMedidaObservada} não restringe vocabulário
     * nenhum. O vocabulário muda com a medida — substância em uso, tipo de aleitamento —, e
     * quem responde pelo par ({@code system}, {@code code}) é o integrador. A Camada 4 não tem
     * como reprovar um valor errado aqui.
     */
    VALOR_MEDIDA,

    /** {@code "DE_PE"} → {@code "LA11870-5"} (LOINC) ou {@code "1"} (BRPosicaoIndividuo). */
    POSICAO_INDIVIDUO,

    /** {@code "PULSO_ESQ"} → {@code "6"} (BRLocalAfericao) — onde a pressão foi aferida. */
    LOCAL_AFERICAO,

    /** {@code "MEDIDO"} → {@code "3137-7"} (LOINC) — medido ou relatado. */
    ORIGEM_MEDIDA,

    /** {@code "COM_SAPATOS"} → {@code "LA11873-9"} (LOINC) — vestimenta na pesagem. */
    ROUPAS_USADAS_MEDICAO,

    /** {@code "RARO"} → {@code "uma-ou-duas"} (BRFrequenciaUsoSubstancia). */
    FREQUENCIA_USO_SUBSTANCIA,

    // ── Transversal: CMD ─────────────────────────────────────────────
    /**
     * {@code "COMATOSO"} → {@code "03"} (BRJustificativaIndividuoNaoIdentificado).
     *
     * <p>Único domínio que atravessa quatro serviços pelo mesmo motivo: a extensão
     * {@code BRIndividuoNaoIdentificado-1.0} é do CMD, e todo documento derivado dele a
     * carrega. Seis códigos no artefato distribuído — a Tabela 3 do manual do SA lista cinco.
     */
    JUSTIFICATIVA_NAO_IDENTIFICADO
}
