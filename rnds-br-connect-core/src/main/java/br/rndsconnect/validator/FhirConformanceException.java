package br.rndsconnect.validator;

import ca.uhn.fhir.validation.SingleValidationMessage;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Lançada pela Camada 4 quando o Bundle não é conforme aos perfis da RNDS.
 *
 * <p>Carrega as mensagens brutas do HAPI porque o valor da camada está exatamente no
 * diagnóstico: caminho FHIR exato, severidade e perfil de origem, em milissegundos e antes
 * de qualquer conexão de rede. Um {@code HTTP 400} da RNDS não fornece nada disso.
 *
 * @author Igor Mendes Domingues Miras
 */
public class FhirConformanceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<SingleValidationMessage> mensagens;

    /**
     * @param mensagens mensagens de validação produzidas pelo HAPI
     */
    public FhirConformanceException(List<SingleValidationMessage> mensagens) {
        super(formatar(mensagens));
        this.mensagens = mensagens;
    }

    private static String formatar(List<SingleValidationMessage> mensagens) {
        return "Bundle reprovado pela validação local contra os perfis da RNDS:"
                + System.lineSeparator()
                + mensagens.stream()
                        .map(m -> "  [" + m.getSeverity() + "] " + m.getLocationString() + ": " + m.getMessage())
                        .collect(Collectors.joining(System.lineSeparator()));
    }

    /** @return as mensagens de validação, para inspeção programática e para o pacote de evidências */
    public List<SingleValidationMessage> getMensagens() {
        return Collections.unmodifiableList(mensagens);
    }
}
