package br.rndsconnect.semantic;

import br.rndsconnect.model.CodedTarget;
import br.rndsconnect.model.TerminologyDomain;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Map;

/**
 * Base das fontes que leem um artefato de texto do classpath.
 *
 * <p>Concentra o que é comum a todos os formatos: leitura do recurso, cálculo do SHA-256 do
 * conteúdo e montagem da string de versão que a proveniência carrega. A carga é feita uma
 * vez, na construção — falha de fonte é falha de inicialização, não de tradução.
 *
 * @author Igor Mendes Domingues Miras
 */
public abstract class AbstractFileMappingSource implements MappingSource {

    private final TerminologyDomain domain;
    private final String recurso;
    private final Map<String, CodedTarget> tabela;
    private final String version;

    /**
     * @param domain domínio que esta fonte alimenta
     * @param recurso caminho do artefato no classpath
     */
    protected AbstractFileMappingSource(TerminologyDomain domain, String recurso) {
        this.domain = domain;
        this.recurso = recurso;

        byte[] conteudo = lerBytes(recurso);
        this.tabela = Collections.unmodifiableMap(parse(conteudo));
        this.version = String.format(
                "%s · sha256:%s · %d entradas", nomeSimples(recurso), sha256Curto(conteudo), tabela.size());

        if (tabela.isEmpty()) {
            throw new MappingSourceException("Fonte de mapeamento vazia: " + recurso + " (domínio " + domain + "). "
                    + "Uma fonte sem entradas reprova todo código do domínio — "
                    + "se a intenção era não mapear este domínio, não registre a estratégia.");
        }
    }

    /**
     * @param conteudo bytes do artefato
     * @return tabela código local → alvo
     */
    protected abstract Map<String, CodedTarget> parse(byte[] conteudo);

    @Override
    public TerminologyDomain getDomain() {
        return domain;
    }

    @Override
    public Map<String, CodedTarget> load() {
        return tabela;
    }

    @Override
    public String getVersion() {
        return version;
    }

    /** @return caminho do artefato no classpath */
    protected String getRecurso() {
        return recurso;
    }

    private static byte[] lerBytes(String recurso) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        InputStream in = cl != null ? cl.getResourceAsStream(recurso) : null;
        if (in == null) {
            in = AbstractFileMappingSource.class.getClassLoader().getResourceAsStream(recurso);
        }
        if (in == null) {
            throw new MappingSourceException("Fonte de mapeamento ausente no classpath: " + recurso);
        }
        try (InputStream stream = in) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new MappingSourceException("Falha ao ler a fonte de mapeamento: " + recurso, e);
        }
    }

    private static String sha256Curto(byte[] conteudo) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(conteudo);
            return HexFormat.of().formatHex(hash).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", e);
        }
    }

    private static String nomeSimples(String recurso) {
        int barra = recurso.lastIndexOf('/');
        return barra >= 0 ? recurso.substring(barra + 1) : recurso;
    }

    /** @param texto bytes UTF-8 a decodificar */
    protected static String utf8(byte[] texto) {
        return new String(texto, StandardCharsets.UTF_8);
    }
}
