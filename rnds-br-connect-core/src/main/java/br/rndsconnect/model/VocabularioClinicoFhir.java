package br.rndsconnect.model;

/**
 * Vocabulários clínicos <strong>fechados</strong>, definidos pelo FHIR base e usados pelo
 * Sumário de Alta.
 *
 * <p>Seguem o mesmo critério de {@link EstadoRegulacao}: vocabulário fechado, enumerado pelo
 * padrão com binding {@code required}, é constante do framework — e não domínio terminológico
 * da Frente B. Pedir ao integrador uma tabela de-para para os seis estados clínicos de uma
 * condição seria transferir trabalho sem transferir escolha: os códigos são os mesmos em
 * qualquer instituição do mundo.
 *
 * <p>Agrupados numa só classe porque nenhum deles tem vida própria fora do SA, e cinco
 * arquivos de dez linhas cada diriam menos que este.
 *
 * @author Igor Mendes Domingues Miras
 */
public final class VocabularioClinicoFhir {

    private VocabularioClinicoFhir() {}

    private static final String HL7 = "http://terminology.hl7.org/CodeSystem/";

    /** {@code Condition.clinicalStatus} — binding {@code required} a {@code condition-clinical}. */
    public enum SituacaoClinica {
        ATIVA("active"),
        RECORRENCIA("recurrence"),
        RECAIDA("relapse"),
        INATIVA("inactive"),
        REMISSAO("remission"),
        RESOLVIDA("resolved");

        private final String codigo;

        SituacaoClinica(String codigo) {
            this.codigo = codigo;
        }

        /** @return o código FHIR */
        public String codigo() {
            return codigo;
        }

        /** @return o CodeSystem do HL7 */
        public static String system() {
            return HL7 + "condition-clinical";
        }
    }

    /** {@code AllergyIntolerance.category} — o que provoca a reação. */
    public enum CategoriaAlergia {
        ALIMENTO("food"),
        MEDICAMENTO("medication"),
        AMBIENTE("environment"),
        BIOLOGICO("biologic");

        private final String codigo;

        CategoriaAlergia(String codigo) {
            this.codigo = codigo;
        }

        /** @return o código FHIR */
        public String codigo() {
            return codigo;
        }
    }

    /** {@code AllergyIntolerance.criticality} — o risco potencial de reação grave. */
    public enum CriticidadeAlergia {
        BAIXA("low"),
        ALTA("high"),
        INDETERMINAVEL("unable-to-assess");

        private final String codigo;

        CriticidadeAlergia(String codigo) {
            this.codigo = codigo;
        }

        /** @return o código FHIR */
        public String codigo() {
            return codigo;
        }
    }

    /** {@code AllergyIntolerance.type} — mecanismo imunológico ou não. */
    public enum TipoAlergia {
        ALERGIA("allergy"),
        INTOLERANCIA("intolerance");

        private final String codigo;

        TipoAlergia(String codigo) {
            this.codigo = codigo;
        }

        /** @return o código FHIR */
        public String codigo() {
            return codigo;
        }
    }

    /** {@code AllergyIntolerance.verificationStatus}. */
    public enum VerificacaoAlergia {
        NAO_CONFIRMADA("unconfirmed"),
        CONFIRMADA("confirmed"),
        REFUTADA("refuted"),
        REGISTRADA_POR_ENGANO("entered-in-error");

        private final String codigo;

        VerificacaoAlergia(String codigo) {
            this.codigo = codigo;
        }

        /** @return o código FHIR */
        public String codigo() {
            return codigo;
        }

        /** @return o CodeSystem do HL7 */
        public static String system() {
            return HL7 + "allergyintolerance-verification";
        }
    }

    /**
     * {@code gender} da extensão {@code BRIndividuoNaoIdentificado-1.0}.
     *
     * <p>O ValueSet {@code BRSexo-1.0} é o {@code administrative-gender} do HL7 restrito a três
     * códigos. Vocabulário fechado do FHIR vira enum, não domínio terminológico.
     */
    public enum SexoIndividuo {
        MASCULINO("male"),
        FEMININO("female"),
        IGNORADO("unknown");

        private final String codigo;

        SexoIndividuo(String codigo) {
            this.codigo = codigo;
        }

        /** @return o código FHIR */
        public String codigo() {
            return codigo;
        }

        /** @return o CodeSystem do HL7 */
        public static String system() {
            return "http://hl7.org/fhir/administrative-gender";
        }
    }

    /** {@code MedicationRequest.status}. */
    public enum SituacaoPrescricao {
        ATIVA("active"),
        SUSPENSA("on-hold"),
        CANCELADA("cancelled"),
        CONCLUIDA("completed"),
        INTERROMPIDA("stopped");

        private final String codigo;

        SituacaoPrescricao(String codigo) {
            this.codigo = codigo;
        }

        /** @return o código FHIR */
        public String codigo() {
            return codigo;
        }
    }

    /** Unidade do período de administração — {@code UnitsOfTime} do FHIR. */
    public enum UnidadeTempo {
        SEGUNDO("s"),
        MINUTO("min"),
        HORA("h"),
        DIA("d"),
        SEMANA("wk"),
        MES("mo"),
        ANO("a");

        private final String codigo;

        UnidadeTempo(String codigo) {
            this.codigo = codigo;
        }

        /** @return o código FHIR */
        public String codigo() {
            return codigo;
        }
    }
}
