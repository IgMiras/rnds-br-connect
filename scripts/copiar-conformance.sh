#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Copia os arquivos de definicao FHIR da RNDS para o classpath do core e gera
# o indice que o ConformancePackage le em tempo de carga.
#
#   uso: ./scripts/copiar-conformance.sh <origem> [subdiretorio] [--append] [--fecho URL]
#
#   exemplos:
#     ./scripts/copiar-conformance.sh /tmp/artefatos/br-core-ril-ria-rel
#     ./scripts/copiar-conformance.sh /tmp/artefatos/Artefatos_Validador_RIRA rira
#     ./scripts/copiar-conformance.sh /tmp/artefatos/modelagem-redfm redfm
#     ./scripts/copiar-conformance.sh /tmp/artefatos/redenacionaldedadosemsaude redfm --append
#     ./scripts/copiar-conformance.sh /tmp/artefatos/modelagem-redfm redfm \
#         --fecho http://www.saude.gov.br/fhir/r4/StructureDefinition/RNDSRegistroEletronicoDispensacaoFornecimentoMedicamento
#
# --estrito, usado junto com --fecho, segue APENAS baseDefinition e bindings
# `required`, ignorando targetProfile. A diferenca e grande: no REDFM o fecho
# normal traz 51 StructureDefinition e o estrito traz 11, porque os br-core-*
# de Condition, Observation, Procedure, Encounter e outros entram apenas como
# alvo de referencias que o servico nunca instancia.
#
# Isso importa porque o custo de gerar snapshot no HAPI e QUADRATICO no numero
# de StructureDefinition do pacote: cada geracao converte todos eles de R4 para
# R5. Com 51, a JVM nao completa nem com 6 GB de heap. Com 11, completa.
#
# Use --estrito quando o pacote vier de projeto Simplifier (que inclui o IG
# inteiro) e o servico usar poucos perfis. Para pacotes de distribuicao, como
# os do RIA, REL e RIRA, o fecho normal e suficiente.
#
# --fecho aceita VARIAS URLs separadas por virgula. No modo --estrito isso e
# necessario: partindo so do perfil de documento, o fecho nao alcanca os demais
# resources do Bundle, porque `section.entry` e targetProfile -- justamente o
# que o modo estrito ignora. Informe os perfis que o Bundle INSTANCIA.
#
# --fecho <URL[,URL...]> copia APENAS o fechamento transitivo dos perfis informados, em vez
# do diretorio inteiro. E indispensavel para pacotes que sao repositorio de
# trabalho do Simplifier: o do REDFM tem 414 artefatos e 596 mil conceitos, dos
# quais o servico usa 102 artefatos e 51 mil conceitos. Carregar os outros 312
# custa 68 MB e derruba a JVM de teste com OutOfMemoryError -- por artefatos que
# pertencem a OUTROS servicos e que o REDFM nunca referencia.
#
# UM PACOTE POR SERVICO. O DATASUS distribui pacotes distintos por servico, e
# eles DIVERGEM entre si: 153 de 402 URLs canonicas carregam conteudos
# diferentes conforme a origem, COM A MESMA VERSAO DECLARADA (defeito no. 11).
# Por isso o destino e parametrizavel e os pacotes convivem lado a lado, cada
# um no seu subdiretorio.
#
# A opcao --append acrescenta ao pacote existente em vez de recria-lo. E o que
# permite ao REDFM combinar seu proprio pacote XML com o IG BR-Core em JSON,
# do qual oito de seus perfis derivam.
#
# Varre RECURSIVAMENTE. A estrutura de subpastas e PRESERVADA no destino: o
# index.txt guarda caminhos relativos, o que elimina colisao de nomes entre
# subpastas e mantem o pacote parecido com a distribuicao oficial.
#
# DIRETORIOS DE CONTROLE SAO IGNORADOS. Nem todo pacote publicado pelo DATASUS
# e um pacote de DISTRIBUICAO: alguns sao o repositorio de trabalho do
# Simplifier, com o historico de edicao junto. Ler .simplifier/ ou
# .forge-corrected-files/ equivale a ler o .git de um repositorio -- sao copias
# de trabalho da ferramenta, nao conteudo publicado, e produzem dezenas de
# colisoes de URL canonica que NAO existem no material distribuido. No pacote
# do REDFM sao 12 colisoes com esses diretorios e 5 sem eles.
#
# Fonte dos artefatos: Portal de Servicos do DATASUS, "arquivos de definicao
# da adocao FHIR R4", e o IG BR-Core no Simplifier (projeto
# RedeNacionaldeDadosemSaude), este ultimo em JSON.
# ---------------------------------------------------------------------------
set -euo pipefail

ORIGEM="${1:?uso: $0 <origem> [subdiretorio-destino] [--append]}"
SUB="${2:-}"
APPEND=0
FECHO=""
ESTRITO=0
args=("${@:2}")
i=0
while [ $i -lt ${#args[@]} ]; do
  case "${args[$i]}" in
    --append) APPEND=1 ;;
    --fecho)  i=$((i + 1)); FECHO="${args[$i]:-}" ;;
    --estrito) ESTRITO=1 ;;
  esac
  i=$((i + 1))
done
# o terceiro argumento pode ser --append; nesse caso SUB continua valendo
case "${SUB:-}" in --append|--fecho|--estrito) SUB="" ;; esac

BASE="rnds-br-connect-core/src/main/resources/conformance"
DESTINO="$BASE${SUB:+/$SUB}"

if [ ! -d "$ORIGEM" ]; then
  echo "ERRO: diretorio de origem nao existe: $ORIGEM" >&2
  exit 1
fi
if [ ! -d "rnds-br-connect-core" ]; then
  echo "ERRO: rode a partir da raiz do repositorio." >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# 1. Inventario: so artefatos de conformidade, e sem URL canonica duplicada.
#
# A guarda de duplicidade vale DENTRO de um pacote. Colisao ENTRE pacotes de
# servicos distintos e o comportamento esperado -- e o motivo de eles serem
# separados. Duas copias do mesmo artefato no MESMO pacote, por outro lado,
# tornariam a validacao dependente da ordem de leitura dos arquivos.
# ---------------------------------------------------------------------------
# Diretorios de controle de ferramenta e sufixos de copia de trabalho.
IGNORAR_DIRS=".simplifier .forge-corrected-files arquivos-marcos-desenv"

extrair_url() {
  # A URL canonica e extraida com um PARSER, nao com regex. Duas razoes, ambas
  # aprendidas na pratica sobre os pacotes oficiais:
  #   1. no XML, <url> e value= podem estar em LINHAS DIFERENTES;
  #   2. no JSON, o primeiro "url" do arquivo costuma ser o de uma EXTENSAO
  #      declarada em meta -- tipicamente structuredefinition-category, do
  #      proprio HL7 -- e nao a URL canonica do recurso.
  # Um grep ingenuo erra nos dois casos, e o erro so aparece como colisao
  # fantasma ou como artefato que escapa da guarda de duplicidade.
  python3 - "$1" <<'PYEOF'
import sys, json
import xml.etree.ElementTree as ET
f = sys.argv[1]
try:
    if f.lower().endswith(".json"):
        print(json.load(open(f, encoding="utf-8")).get("url") or "")
    else:
        r = ET.parse(f).getroot()
        e = r.find("{http://hl7.org/fhir}url")
        print(e.get("value") if e is not None else "")
except Exception:
    print("")
PYEOF
}

deve_ignorar() {
  local rel="$1"
  local d
  for d in $IGNORAR_DIRS; do
    case "/$rel/" in */"$d"/*) return 0 ;; esac
  done
  case "$(basename "$rel")" in
    *-duplicate-[0-9].*) return 0 ;;
    *\ \([0-9]\).*) return 0 ;;
  esac
  return 1
}

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
MAPA="$TMP/urls.txt"
INDICE_EXISTENTE="$DESTINO/index.txt"
URLS_EXISTENTES="$TMP/urls_existentes.txt"
: > "$URLS_EXISTENTES"
if [ "$APPEND" -eq 1 ] && [ -f "$INDICE_EXISTENTE" ]; then
  while IFS= read -r linha; do
    case "$linha" in ''|'#'*) continue ;; esac
    arq="$DESTINO/$linha"
    [ -f "$arq" ] || continue
    u="$(extrair_url "$arq")"
    [ -n "$u" ] && echo "$u" >> "$URLS_EXISTENTES"
  done < "$INDICE_EXISTENTE"
fi
: > "$MAPA"
ignorados_controle=0

while IFS= read -r -d '' arquivo; do
  rel="${arquivo#"$ORIGEM"/}"
  if deve_ignorar "$rel"; then
    ignorados_controle=$((ignorados_controle + 1))
    continue
  fi
  tipo="$(grep -m1 -oE '<(StructureDefinition|ValueSet|CodeSystem)\b' "$arquivo" 2>/dev/null | head -1 || true)"
  if [ -z "$tipo" ]; then
    # tenta JSON (o IG BR-Core vem do Simplifier nesse formato)
    tipo="$(grep -m1 -oE '"resourceType"[[:space:]]*:[[:space:]]*"(StructureDefinition|ValueSet|CodeSystem)"' "$arquivo" 2>/dev/null | head -1 || true)"
  fi
  if [ -z "$tipo" ]; then
    echo "  . ignorado (nao e artefato de conformidade): $rel"
    continue
  fi
  url="$(extrair_url "$arquivo")"
  [ -z "$url" ] && url="(sem-url)"
  printf '%s\t%s\n' "$url" "$rel" >> "$MAPA"
done < <(find "$ORIGEM" \( -name '*.xml' -o -name '*.json' \) -type f -print0)

if [ "$ignorados_controle" -gt 0 ]; then
  echo "  . $ignorados_controle arquivo(s) de diretorio de controle da ferramenta ignorado(s)"
  echo "    (.simplifier/, .forge-corrected-files/, sufixos -duplicate-N e \" (N)\")"
fi


# ---------------------------------------------------------------------------
# 1b. Fechamento transitivo, quando --fecho foi informado.
#
# Mantem no mapa apenas os artefatos ALCANCAVEIS a partir do perfil-raiz,
# seguindo baseDefinition, type.profile, type.targetProfile, binding.valueSet e
# compose.include/exclude.system. Elementos com max=0 sao ignorados: um ramo que
# o perfil PROIBE nao arrasta dependencia.
#
# Referencias para fora do pacote (hl7.org, terminology.hl7.org) sao ignoradas,
# porque o HAPI as resolve pelo DefaultProfileValidationSupport.
# ---------------------------------------------------------------------------
if [ -n "$FECHO" ]; then
  MAPA_FECHO="$TMP/urls_fecho.txt"
  python3 - "$ORIGEM" "$MAPA" "$FECHO" "$MAPA_FECHO" "$ESTRITO" <<'PYEOF'
import sys, os, json
import xml.etree.ElementTree as ET

origem, mapa, raiz, saida = sys.argv[1:5]
estrito = len(sys.argv) > 5 and sys.argv[5] == "1"
NS = "{http://hl7.org/fhir}"
ns = {"f": "http://hl7.org/fhir"}

idx = {}
for linha in open(mapa, encoding="utf-8"):
    partes = linha.rstrip("\n").split("\t")
    if len(partes) == 2 and partes[0] != "(sem-url)":
        idx.setdefault(partes[0], partes[1])

def carregar(rel):
    f = os.path.join(origem, rel)
    try:
        if f.lower().endswith(".json"):
            return "json", json.load(open(f, encoding="utf-8"))
        return "xml", ET.parse(f).getroot()
    except Exception:
        return None, None

def elementos(fmt, d):
    """(id, max, [type.profile], [type.targetProfile], binding_vs, strength, discriminadores)."""
    out = []
    if fmt == "json":
        for parte in ("differential", "snapshot"):
            for el in (d.get(parte, {}) or {}).get("element", []) or []:
                tys = el.get("type", []) or []
                b = el.get("binding") or {}
                disc = [x.get("type") for x in ((el.get("slicing") or {}).get("discriminator") or [])]
                out.append((el.get("id") or "", str(el.get("max") or ""),
                            [p for t in tys for p in (t.get("profile") or [])],
                            [p for t in tys for p in (t.get("targetProfile") or [])],
                            b.get("valueSet"), b.get("strength"), disc))
    else:
        for el in d.iter(NS + "element"):
            mx = el.find("f:max", ns)
            bd = el.find("f:binding", ns)
            vs = st = None
            if bd is not None:
                v = bd.find("f:valueSet", ns); s = bd.find("f:strength", ns)
                vs = v.get("value") if v is not None else None
                st = s.get("value") if s is not None else None
            sl = el.find("f:slicing", ns)
            disc = []
            if sl is not None:
                for dd in sl.findall("f:discriminator", ns):
                    t = dd.find("f:type", ns)
                    if t is not None: disc.append(t.get("value"))
            out.append((el.get("id") or "", mx.get("value") if mx is not None else "",
                        [p.get("value") for t in el.findall("f:type", ns) for p in t.findall("f:profile", ns)],
                        [p.get("value") for t in el.findall("f:type", ns) for p in t.findall("f:targetProfile", ns)],
                        vs, st, disc))
    return out

def base_e_compose(fmt, d):
    out = set()
    if fmt == "json":
        if d.get("baseDefinition"): out.add(d["baseDefinition"])
        for tag in ("include", "exclude"):
            for inc in (d.get("compose", {}) or {}).get(tag, []) or []:
                if inc.get("system"): out.add(inc["system"])
                for v in inc.get("valueSet", []) or []: out.add(v)
    else:
        b = d.find("f:baseDefinition", ns)
        if b is not None: out.add(b.get("value"))
        if d.tag.split("}")[-1] == "ValueSet":
            for tag in ("include", "exclude"):
                for inc in d.iter(NS + tag):
                    sy = inc.find("f:system", ns)
                    if sy is not None: out.add(sy.get("value"))
                    for v in inc.findall("f:valueSet", ns): out.add(v.get("value"))
    return out

def refs(rel, so_estrutura=False):
    """Devolve (referencias a seguir, alvos de discriminador de fatia).

    Modo normal: segue tudo. Modo estrito: segue baseDefinition, type.profile (extensoes sao
    ESTRUTURA, nunca opcionais) e bindings required; ignora type.targetProfile -- EXCETO os
    alvos de fatias discriminadas por perfil, que o validador precisa resolver para decidir a
    fatia de QUALQUER elemento, usado ou nao. Esses voltam como alvos 'estruturais'.
    Modo estrutural: so baseDefinition e type.profile -- sem vocabularios, que nao sao
    consultados quando o perfil serve apenas para decidir a que fatia um recurso pertence."""
    fmt, d = carregar(rel)
    if d is None: return set(), set()
    out = base_e_compose(fmt, d)
    alvos_fatia = set()
    els = elementos(fmt, d)
    fatiados_por_perfil = {i for (i, _mx, _p, _t, _v, _s, disc) in els if "profile" in disc}
    for (i, mx, perfis, alvos, vs, st, _disc) in els:
        if mx == "0": continue
        out.update(perfis)
        if so_estrutura: continue
        if not estrito:
            out.update(alvos)
            if vs: out.add(vs)
            continue
        if vs and st == "required": out.add(vs)
        if any(i.startswith(f + ":") for f in fatiados_por_perfil):
            alvos_fatia.update(alvos)
    limpa = lambda s: {x.split("|")[0] for x in s if x}
    return limpa(out), limpa(alvos_fatia)

raizes = [r.strip() for r in raiz.split(",") if r.strip()]
ausentes = [r for r in raizes if r not in idx]
if len(ausentes) == len(raizes):
    sys.stderr.write("ERRO: nenhum dos perfis-raiz esta na origem: %s\n" % ", ".join(raizes))
    sys.exit(2)
for r in ausentes:
    print("  . perfil-raiz ausente nesta origem (pode estar na outra): %s" % r.split("/")[-1])

fila = [(r, False) for r in raizes if r in idx]
vistos = {}
externas = set()
while fila:
    u, estr = fila.pop()
    if u in vistos and (vistos[u] is False or estr):
        continue
    vistos[u] = estr
    seguir, alvos = refs(idx[u], so_estrutura=estr)
    for x in seguir:
        if x in idx:
            fila.append((x, estr))
        elif not (x.startswith("http://hl7.org/") or x.startswith("http://terminology.hl7.org/")):
            externas.add(x)
    for x in alvos:
        if x in idx and x not in vistos:
            fila.append((x, True))

with open(saida, "w", encoding="utf-8") as fh:
    for u in vistos:
        fh.write("%s\t%s\n" % (u, idx[u]))

n_estr = sum(1 for v in vistos.values() if v)
print("  . fechamento transitivo%s: %d de %d artefatos alcancados a partir do perfil-raiz"
      % (" ESTRITO" if estrito else "", len(vistos), len(idx)))
if n_estr:
    print("  . %d deles so pela estrutura: alvos de discriminador de fatia, sem vocabularios" % n_estr)
if externas:
    print("  . %d referencia(s) fora do pacote (o HAPI resolve, ou faltam mesmo):" % len(externas))
    for u in sorted(externas)[:8]:
        print("      %s" % u)
PYEOF
  rc=$?
  [ $rc -ne 0 ] && exit $rc
  mv "$MAPA_FECHO" "$MAPA"
fi

# No modo --append, artefatos cuja URL canonica JA existe no pacote sao pulados.
# Os pacotes do DATASUS se sobrepoem fortemente: o do REDFM e o IG BR-Core
# compartilham 291 de 324 URLs, porque o projeto Simplifier do REDFM inclui o
# BR-Core inteiro. Sem esse filtro, --append duplicaria quase tudo -- e a
# guarda de duplicidade, que existe para proteger a validacao, abortaria.
# O que ja esta carregado PREVALECE: quem chegou primeiro define a versao.
if [ "$APPEND" -eq 1 ] && [ -f "$INDICE_EXISTENTE" ]; then
  ja=0
  MAPA_FILTRADO="$TMP/urls_filtrado.txt"
  : > "$MAPA_FILTRADO"
  while IFS=$'\t' read -r u rel; do
    if grep -qxF "$u" "$URLS_EXISTENTES" 2>/dev/null; then
      ja=$((ja + 1))
    else
      printf '%s\t%s\n' "$u" "$rel" >> "$MAPA_FILTRADO"
    fi
  done < "$MAPA"
  mv "$MAPA_FILTRADO" "$MAPA"
  [ "$ja" -gt 0 ] && echo "  . $ja artefato(s) ja presente(s) no pacote — mantida a versao existente"
fi

DUPS="$(cut -f1 "$MAPA" | grep -v '^(sem-url)$' | sort | uniq -d || true)"
if [ -n "$DUPS" ]; then
  echo
  echo "ERRO: URL canonica duplicada DENTRO do pacote. Decida qual versao vale"
  echo "      e remova a outra da origem antes de prosseguir:"
  while IFS= read -r u; do
    echo "  $u"
    grep -F "$u	" "$MAPA" | cut -f2 | sed 's/^/      -> /'
  done <<< "$DUPS"
  echo
  echo "  Nota: nomes de arquivo que nao correspondem ao conteudo sao comuns nos"
  echo "  pacotes oficiais -- ha casos de BRBooleano.xml declarando a URL de"
  echo "  BRIndividuoProtegido. Mantenha o arquivo cujo nome bate com a URL."
  exit 1
fi

# ---------------------------------------------------------------------------
# 2. Copia, preservando a estrutura de subpastas.
# ---------------------------------------------------------------------------
if [ "$APPEND" -eq 0 ]; then
  rm -rf "$DESTINO"
fi
mkdir -p "$DESTINO"

INDICE="$DESTINO/index.txt"
if [ "$APPEND" -eq 0 ]; then
  {
    echo "# Artefatos de conformidade da RNDS."
    echo "# GERADO por scripts/copiar-conformance.sh. NAO EDITAR A MAO."
    echo "# Origem: $ORIGEM"
    echo "# Gerado em: $(date -Iseconds)"
  } > "$INDICE"
else
  echo "# --- acrescentado de: $ORIGEM em $(date -Iseconds)" >> "$INDICE"
fi

n=0
while IFS=$'\t' read -r url rel; do
  mkdir -p "$DESTINO/$(dirname "$rel")"
  cp "$ORIGEM/$rel" "$DESTINO/$rel"
  echo "$rel" >> "$INDICE"
  n=$((n + 1))
done < "$MAPA"

echo
echo "$n artefatos copiados para $DESTINO"
echo "   indice: $INDICE"
if [ "$APPEND" -eq 1 ]; then
  echo "   (modo --append: o pacote anterior foi preservado)"
fi
echo
echo "Registre este pacote no builder:"
echo "   .servico(Servico.XXX, \"conformance${SUB:+/$SUB}\")"
