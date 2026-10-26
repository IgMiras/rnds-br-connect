package br.rndsconnect.semantic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Trilha de auditoria de um envio: todas as traduções que produziram um Bundle.
 *
 * <p>Acompanha o Bundle até a Camada 5 e faz parte do pacote de evidências de homologação.
 * Um Bundle sem trilha é um Bundle cujas escolhas terminológicas não podem ser justificadas
 * depois.
 *
 * @author Igor Mendes Domingues Miras
 */
public class MappingAuditLog {

    private final List<MappingProvenance> registros = new ArrayList<>();

    /** @param proveniencia tradução a registrar */
    public void registrar(MappingProvenance proveniencia) {
        registros.add(proveniencia);
    }

    /** @param valor resultado de tradução cuja proveniência será registrada */
    public void registrar(MappedValue valor) {
        registros.add(valor.provenance());
    }

    /** @return as traduções, na ordem em que ocorreram */
    public List<MappingProvenance> getRegistros() {
        return Collections.unmodifiableList(registros);
    }

    /** @return quantidade de traduções registradas */
    public int size() {
        return registros.size();
    }

    /**
     * @return a trilha completa em texto, uma tradução por linha
     */
    public String toRelatorio() {
        return registros.stream().map(MappingProvenance::toLinha).collect(Collectors.joining(System.lineSeparator()));
    }
}
