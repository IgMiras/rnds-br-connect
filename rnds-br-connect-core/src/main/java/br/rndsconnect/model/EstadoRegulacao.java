package br.rndsconnect.model;

/**
 * Os sete estados por que uma solicitação de regulação assistencial pode passar.
 *
 * <p>Correspondem às fatias de {@code Composition.event} no perfil
 * {@code BRRegulacaoAssistencial} e aos códigos do CodeSystem
 * {@code BRStatusRegulacaoAssistencial}.
 *
 * <p><strong>Por que é enum, e não domínio terminológico.</strong> Os demais vocabulários da
 * RNDS têm centenas ou milhares de códigos, variam com o tempo e precisam ser mapeados a
 * partir do vocabulário local — daí a Frente B. Este tem <strong>sete valores fixos</strong>,
 * definidos pelo próprio perfil, e cada um corresponde a uma fatia declarada. Pedir ao
 * integrador que mapeie sete estados que o framework já conhece seria transferir trabalho sem
 * transferir decisão.
 *
 * <p>A distinção segue o mesmo critério do {@code Composition.type} e do
 * {@code Composition.status}, que os Builders fixam em vez de mapear (Arquitetura §7.3.2).
 *
 * @author Igor Mendes Domingues Miras
 */
public enum EstadoRegulacao {

    /** Solicitação em espera. */
    PENDENTE("pending"),

    /** Devolvida ao solicitante. */
    DEVOLVIDA("returned-to-requester"),

    /** Agendada. */
    AGENDADA("booked"),

    /** Atendida ou internada. */
    ATENDIDA("attended"),

    /** Cancelada. */
    CANCELADA("cancelled"),

    /** Falta do paciente. */
    FALTA("absence"),

    /** Excluída. */
    EXCLUIDA("excluded");

    /** URL canônica do CodeSystem de estados. */
    public static final String SYSTEM = "http://www.saude.gov.br/fhir/r4/CodeSystem/BRStatusRegulacaoAssistencial";

    private final String codigo;

    EstadoRegulacao(String codigo) {
        this.codigo = codigo;
    }

    /** @return o código como o perfil o declara */
    public String codigo() {
        return codigo;
    }

    /** @return o par ({@code system}, {@code code}) que a Camada 3 emite */
    public CodedValue toCodedValue() {
        return new CodedValue(SYSTEM, codigo);
    }
}
