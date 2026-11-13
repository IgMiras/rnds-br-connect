package br.rndsconnect.catalogo;

import br.rndsconnect.model.CodedValue;
import java.io.ByteArrayInputStream;
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
 * Catálogo dos códigos que a RNDS aceita num elemento codificado: a lista que um vínculo
 * {@code required} admite, extraída dos artefatos oficiais de conformidade.
 *
 * <h2>O que um catálogo é, e o que não é</h2>
 *
 * <p>Ele responde <strong>"este código é aceito pela RNDS?"</strong>. Não responde
 * <strong>"qual código corresponde ao meu exame, ou ao que eu medi?"</strong>. A segunda
 * pergunta é decisão clínica, depende do que a instituição de fato faz, e permanece com a
 * equipe de saúde (ADR-05).
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
 * Camada 4 — mas só quando o primeiro paciente daquele código for enviado, possivelmente meses
 * depois. Ver {@link MapeamentoConformeCatalogo}.
 *
 * <h2>Um formato, uma classe</h2>
 *
 * <p>Todo catálogo é um YAML <strong>gerado</strong> por um script de {@code scripts/} a partir
 * dos artefatos oficiais, com o SHA-256 das fontes no cabeçalho e duas chaves:
 *
 * <pre>
 * totalDeclarado: 475
 * codigos:
 *   http://www.saude.gov.br/fhir/r4/CodeSystem/BRNomeExameLOINC:
 *     "94309-2": "SARS Coronavírus 2, [presença de] RNA ..."
 * </pre>
 *
 * <p>Esta classe lê qualquer arquivo nesse formato. Os catálogos distribuídos hoje têm
 * atalhos próprios, {@link #exames()} e {@link #medidas()}; os demais entram por
 * {@link #carregar(String)}.
 *
 * <p>A carga confere o total declarado contra o total carregado e falha se divergirem. Foi
 * essa verificação que revelou os dois códigos repetidos do {@code BRNomeExameLOINC}: o
 * gerador contava linhas escritas e o parser carregava chaves distintas, e os números não
 * bateram.
 *
 * @author Igor Mendes Domingues Miras
 */
public final class CatalogoCodigosAceitos {

    /** Caminho, no classpath, do catálogo dos nomes de exame do REL. */
    public static final String RECURSO_EXAMES = "catalogo/exames-rnds.yml";

    /** Caminho, no classpath, do catálogo das medidas observadas do RAC. */
    public static final String RECURSO_MEDIDAS = "catalogo/medidas-rnds.yml";

    private final String recurso;
    private final Map<String, Map<String, String>> porSystem;
    private final int total;
    private final String sha256;

    private CatalogoCodigosAceitos(
            String recurso, Map<String, Map<String, String>> porSystem, int total, String sha256) {
        this.recurso = recurso;
        this.porSystem = porSystem;
        this.total = total;
        this.sha256 = sha256;
    }

    /**
     * Catálogo dos nomes de exame do REL: os códigos que o {@code BRNomeExame-2.0} aceita em
     * {@code Observation.code}.
     *
     * <p>São 475: 442 códigos LOINC e 33 do {@code BRNomeExameGAL}. Chegar a 442 exige
     * <strong>duas</strong> correções sobre os 451 conceitos do {@code BRNomeExameLOINC}:
     *
     * <ol>
     *   <li><strong>Deduplicar.</strong> Os códigos {@code 80205-8} e {@code 80206-6} são
     *       declarados duas vezes cada, com displays que traduzem o mesmo termo LOINC de formas
     *       diferentes. O CodeSystem não declara {@code count}, de modo que nada no artefato
     *       denuncia a discrepância: são 451 conceitos e 449 códigos.
     *   <li><strong>Aplicar o {@code compose.exclude}.</strong> O ValueSet remove 7 códigos que
     *       existem no CodeSystem e não são aceitos.
     * </ol>
     *
     * <p>Somar os conceitos dos dois CodeSystems dá <strong>484</strong>; parar após o
     * {@code exclude} dá <strong>477</strong>. Nenhum dos dois é o número de códigos aceitos, e
     * um catálogo com qualquer um deles incluiria entradas que a RNDS recusa ou contaria a mesma
     * entrada duas vezes — o oposto do propósito.
     *
     * <p>Gerado por {@code scripts/gerar-catalogo-exames.py}.
     *
     * @return o catálogo carregado do classpath
     */
    public static CatalogoCodigosAceitos exames() {
        return carregar(RECURSO_EXAMES);
    }

    /**
     * Catálogo das medidas observadas do RAC: os códigos que o {@code BRTipoObservacao-1.0}
     * aceita em {@code Observation.code} do {@code BRMedidaObservada}.
     *
     * <p>São 23, de três fontes: os 9 códigos do CodeSystem {@code BRTipoObservacao}, incluído
     * por inteiro, 1 código da {@code BRTabelaSUS} e 13 códigos LOINC, estes dois enumerados no
     * próprio ValueSet. O LOINC entra com a barra final, {@code https://loinc.org/}.
     *
     * <p>O arquivo traz ainda duas listas que esta classe não lê, porque não são códigos
     * aceitos e sim achados do artefato: os pares de sinônimos ({@code sinonimos}) e os códigos
     * que não são medida de valor ({@code naoSaoMedida}).
     *
     * <p>Gerado por {@code scripts/gerar-catalogo-medidas.py}.
     *
     * @return o catálogo carregado do classpath
     */
    public static CatalogoCodigosAceitos medidas() {
        return carregar(RECURSO_MEDIDAS);
    }

    /**
     * @param recurso caminho do catálogo no classpath
     * @return o catálogo carregado
     * @throws IllegalStateException se o arquivo faltar, estiver malformado ou contiver um
     *     número de códigos diferente do que declara
     */
    public static CatalogoCodigosAceitos carregar(String recurso) {
        byte[] conteudo = lerBytes(recurso);
        // SafeConstructor: o YAML é dado de configuração, não código.
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Object raiz = yaml.load(new ByteArrayInputStream(conteudo));
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
                    + ". Regenere-o com o script indicado no cabeçalho do arquivo.");
        }

        return new CatalogoCodigosAceitos(recurso, Collections.unmodifiableMap(tabela), contados, sha256(conteudo));
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
     * Os códigos aceitos, por CodeSystem, cada um com o display oficial.
     *
     * <p>É a forma em que o teste de portão compara o catálogo com o que os artefatos
     * declaram, e serve também a quem preenche a tabela da instituição e precisa da lista.
     *
     * @return mapa imutável: URL do CodeSystem → (código → display)
     */
    public Map<String, Map<String, String>> codigos() {
        return porSystem;
    }

    /** @return o caminho, no classpath, do arquivo de que este catálogo foi carregado */
    public String recurso() {
        return recurso;
    }

    /**
     * SHA-256 do arquivo carregado.
     *
     * <p>Permite registrar, na proveniência ou no pacote de evidências, contra qual versão do
     * catálogo uma tabela de mapeamento foi conferida.
     *
     * @return os 16 primeiros dígitos hexadecimais do hash
     */
    public String sha256() {
        return sha256;
    }

    private static byte[] lerBytes(String recurso) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        InputStream in = cl != null ? cl.getResourceAsStream(recurso) : null;
        if (in == null) {
            in = CatalogoCodigosAceitos.class.getClassLoader().getResourceAsStream(recurso);
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
