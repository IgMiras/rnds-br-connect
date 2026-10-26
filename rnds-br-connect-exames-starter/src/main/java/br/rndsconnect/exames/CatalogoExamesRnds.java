package br.rndsconnect.exames;

import br.rndsconnect.model.CodedValue;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Catálogo dos códigos de exame que a RNDS aceita — 475 no total.
 *
 * <h2>O que este catálogo é, e o que não é</h2>
 *
 * <p>Ele responde <strong>"este código é aceito pela RNDS?"</strong>. Não responde
 * <strong>"qual código corresponde ao meu exame?"</strong>. A segunda pergunta é decisão
 * clínica, depende do que o laboratório de fato mede, e permanece com a equipe de saúde
 * (ADR-05).
 *
 * <p>A distinção não é retórica. Um framework que distribuísse equivalências
 * <em>"HEMOGRAMA_COMPLETO → 58410-2"</em> estaria tomando decisão diagnóstica por conta
 * própria, com base num nome de coluna de banco de dados. Distribuir o catálogo do que é
 * aceitável é infraestrutura; distribuir a correspondência seria exercício ilegítimo de
 * competência clínica.
 *
 * <h2>Para que serve na prática</h2>
 *
 * <p>Para conferir a tabela da instituição <strong>na inicialização</strong>. Uma
 * correspondência que aponte para código fora do catálogo produz Bundle reprovado pela
 * Camada 4 — mas só quando o primeiro paciente daquele exame for enviado, possivelmente meses
 * depois. Ver {@link MapeamentoConformeCatalogo}.
 *
 * <h2>Composição</h2>
 *
 * <p>442 códigos LOINC e 33 do {@code BRNomeExameGAL}. Chegar a 442 exige <strong>duas</strong>
 * correções sobre os 451 conceitos do {@code BRNomeExameLOINC}:
 *
 * <ol>
 *   <li><strong>Deduplicar.</strong> Os códigos {@code 80205-8} e {@code 80206-6} são
 *       declarados duas vezes cada, com displays que traduzem o mesmo termo LOINC de formas
 *       diferentes. O CodeSystem não declara {@code count}, de modo que nada no artefato
 *       denuncia a discrepância: são 451 conceitos e 449 códigos.
 *   <li><strong>Aplicar o {@code compose.exclude}.</strong> O ValueSet {@code BRNomeExame-2.0}
 *       remove 7 códigos que existem no CodeSystem e não são aceitos.
 * </ol>
 *
 * <p>Somar os conceitos dos dois CodeSystems dá <strong>484</strong>; parar após o
 * {@code exclude} dá <strong>477</strong>. Nenhum dos dois é o número de códigos aceitos, e um
 * catálogo com qualquer um deles incluiria entradas que a RNDS recusa ou contaria a mesma
 * entrada duas vezes — o oposto do propósito.
 *
 * <p>O arquivo é <strong>gerado</strong> por {@code scripts/gerar-catalogo-exames.py} a partir
 * dos artefatos oficiais, e carrega no cabeçalho o SHA-256 das fontes que o originaram.
 *
 * <p>A carga confere o total declarado contra o total carregado e falha se divergirem. Foi
 * essa verificação que revelou a duplicidade: o gerador contava linhas escritas e o parser
 * carregava chaves distintas, e os números não bateram.
 *
 * @author Igor Mendes Domingues Miras
 */
public final class CatalogoExamesRnds {

    /** Caminho padrão do catálogo no classpath. */
    public static final String RECURSO_PADRAO = "catalogo/exames-rnds.yml";

    private static final String CS = "http://www.saude.gov.br/fhir/r4/CodeSystem/";
    /** URL canônica do CodeSystem LOINC da RNDS. */
    public static final String SYSTEM_LOINC = CS + "BRNomeExameLOINC";
    /** URL canônica do CodeSystem GAL da RNDS. */
    public static final String SYSTEM_GAL = CS + "BRNomeExameGAL";

    private final Map<String, Map<String, String>> porSystem;
    private final int total;
    private final String sha256;

    private CatalogoExamesRnds(Map<String, Map<String, String>> porSystem, int total, String sha256) {
        this.porSystem = porSystem;
        this.total = total;
        this.sha256 = sha256;
    }

    /** @return o catálogo carregado do classpath */
    public static CatalogoExamesRnds carregar() {
        return carregar(RECURSO_PADRAO);
    }

    /**
     * @param recurso caminho do catálogo no classpath
     * @return o catálogo carregado
     */
    @SuppressWarnings("unchecked")
    public static CatalogoExamesRnds carregar(String recurso) {
        byte[] conteudo = lerBytes(recurso);
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Object raiz = yaml.load(new java.io.ByteArrayInputStream(conteudo));
        if (!(raiz instanceof Map<?, ?> mapa)) {
            throw new IllegalStateException("Catálogo malformado: " + recurso);
        }

        Object bruto = mapa.get("codigos");
        if (!(bruto instanceof Map<?, ?> porSys)) {
            throw new IllegalStateException("Chave 'codigos' ausente em " + recurso);
        }

        Map<String, Map<String, String>> tabela = new LinkedHashMap<>();
        int contados = 0;
        for (Map.Entry<?, ?> e : porSys.entrySet()) {
            Map<String, String> codigos = new LinkedHashMap<>();
            if (e.getValue() instanceof Map<?, ?> m) {
                for (Map.Entry<?, ?> c : m.entrySet()) {
                    codigos.put(String.valueOf(c.getKey()), String.valueOf(c.getValue()));
                }
            }
            contados += codigos.size();
            tabela.put(String.valueOf(e.getKey()), Collections.unmodifiableMap(codigos));
        }

        Object declarado = mapa.get("totalDeclarado");
        if (declarado != null && !String.valueOf(declarado).equals(String.valueOf(contados))) {
            throw new IllegalStateException("Catálogo inconsistente em " + recurso + ": declara "
                    + declarado + " códigos e contém " + contados
                    + ". Regenere com scripts/gerar-catalogo-exames.py.");
        }

        return new CatalogoExamesRnds(Collections.unmodifiableMap(tabela), contados, sha256(conteudo));
    }

    /**
     * @param system URL canônica do CodeSystem
     * @param code código
     * @return {@code true} se a RNDS aceita este par
     */
    public boolean aceita(String system, String code) {
        Map<String, String> m = porSystem.get(system);
        return m != null && m.containsKey(code);
    }

    /**
     * @param alvo par ({@code system}, {@code code})
     * @return {@code true} se a RNDS aceita este par
     */
    public boolean aceita(CodedValue alvo) {
        return alvo != null && aceita(alvo.system(), alvo.code());
    }

    /**
     * @param system URL canônica do CodeSystem
     * @param code código
     * @return o display oficial, se o código for aceito
     */
    public Optional<String> display(String system, String code) {
        Map<String, String> m = porSystem.get(system);
        return m == null ? Optional.empty() : Optional.ofNullable(m.get(code));
    }

    /** @return total de códigos aceitos */
    public int total() {
        return total;
    }

    /**
     * @param system URL canônica do CodeSystem
     * @return quantos códigos daquele CodeSystem são aceitos
     */
    public int totalDe(String system) {
        Map<String, String> m = porSystem.get(system);
        return m == null ? 0 : m.size();
    }

    /** @return os CodeSystems que compõem o catálogo */
    public Set<String> systems() {
        return porSystem.keySet();
    }

    /**
     * SHA-256 do arquivo carregado.
     *
     * <p>Permite registrar, na proveniência ou no pacote de evidências, contra qual versão do
     * catálogo uma tabela de mapeamento foi conferida.
     *
     * @return hash hexadecimal
     */
    public String sha256() {
        return sha256;
    }

    private static byte[] lerBytes(String recurso) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        InputStream in = cl != null ? cl.getResourceAsStream(recurso) : null;
        if (in == null) {
            in = CatalogoExamesRnds.class.getClassLoader().getResourceAsStream(recurso);
        }
        if (in == null) {
            throw new IllegalStateException("Catálogo ausente no classpath: " + recurso);
        }
        try (InputStream stream = in) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler o catálogo: " + recurso, e);
        }
    }

    private static String sha256(byte[] conteudo) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo))
                    .substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", e);
        }
    }
}
