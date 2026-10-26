package br.rndsconnect.semantic;

import br.rndsconnect.model.TerminologyDomain;
import java.util.Objects;

/**
 * Um código no vocabulário local, antes da tradução.
 *
 * @param domain domínio terminológico ao qual o código pertence
 * @param value o código como o sistema cliente o conhece
 * @author Igor Mendes Domingues Miras
 */
public record RawValue(TerminologyDomain domain, String value) {
    public RawValue {
        Objects.requireNonNull(domain, "domain do RawValue não pode ser nulo");
        Objects.requireNonNull(value, "value do RawValue não pode ser nulo");
    }
}
