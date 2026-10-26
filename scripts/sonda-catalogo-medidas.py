#!/usr/bin/env python3
"""Sonda do item 12: o catalogo de codigos aceitos nas medidas observadas.

SO MEDE. Nao escreve arquivo nenhum.

Responde quatro coisas que eu nao consigo medir no meu espelho do
repositorio, que nao tem o pacote conformance/rac:

  1. o BRMedidaObservada.code aponta mesmo para o BRTipoObservacao-1.0?
  2. a copia do ValueSet no pacote do RAC e identica a do SA, ou divergem
     (defeito no. 11)?
  3. quantos codigos o catalogo teria, pelas tres fontes que o ValueSet
     compoe;
  4. quais codigos sao SINONIMOS dentro do mesmo ValueSet -- a mesma medida
     com duas grafias oficiais.

  uso: python3 scripts/sonda-catalogo-medidas.py [pacote]
       (a partir da raiz do repositorio; pacote = rac, sa, ...; default: rac)
"""
import hashlib
import os
import sys
import xml.etree.ElementTree as ET

NS = "{http://hl7.org/fhir}"
RAIZ = "rnds-br-connect-core/src/main/resources/conformance"
VS_URL = "http://www.saude.gov.br/fhir/r4/ValueSet/BRTipoObservacao-1.0"

# Pares de sinonimo conhecidos, por MEDIDA, para o relatorio. Nao e tabela de
# equivalencia clinica (ADR-05): e a constatacao de que o proprio ValueSet
# oferece duas grafias oficiais para a mesma coisa.
SINONIMOS = {
    "Peso": ("P", "29463-7"),
    "Altura": ("A", "8302-2"),
    "Perimetro cefalico": ("PC", "9843-4"),
    "Circunferencia abdominal": ("CA", "8280-0"),
    "Pressao arterial": ("PA", "8480-6 / 8462-4"),
}


def arquivos(pacote):
    d = os.path.join(RAIZ, pacote)
    idx = os.path.join(d, "index.txt")
    if not os.path.exists(idx):
        return None, None
    out = []
    for rel in [l.strip() for l in open(idx, encoding="utf-8") if l.strip() and not l.startswith("#")]:
        if rel.lower().endswith(".xml"):
            out.append(os.path.join(d, rel))
    return d, out


def por_url(lista, sufixo, tipo):
    for f in lista:
        try:
            r = ET.parse(f).getroot()
        except Exception:
            continue
        if r.tag != NS + tipo:
            continue
        u = r.find(NS + "url")
        if u is not None and u.get("value", "").endswith(sufixo):
            return f, r
    return None, None


def valor(r, tag):
    e = r.find(NS + tag)
    return e.get("value") if e is not None else None


def main(pacote):
    d, lista = arquivos(pacote)
    if lista is None:
        sys.exit("ERRO: pacote nao encontrado: %s/%s (rode a partir da raiz do repositorio)"
                 % (RAIZ, pacote))
    print("=" * 92)
    print("SONDA DO ITEM 12 — catalogo de codigos das medidas observadas · pacote %s" % pacote)
    print("=" * 92)

    # ── 1. O binding do BRMedidaObservada.code ──────────────────────────
    f_sd, sd = por_url(lista, "/BRMedidaObservada", "StructureDefinition")
    if sd is None:
        print("\n[1] BRMedidaObservada: AUSENTE neste pacote.")
    else:
        print("\n[1] BRMedidaObservada v%s (%s)" % (valor(sd, "version"), os.path.basename(f_sd)))
        achou = False
        for parte in ("snapshot", "differential"):
            b = sd.find(NS + parte)
            if b is None:
                continue
            for el in b.findall(NS + "element"):
                i = el.get("id") or ""
                if i not in ("Observation.code", "Observation.code.coding"):
                    continue
                bd = el.find(NS + "binding")
                if bd is None:
                    continue
                vs = bd.find(NS + "valueSet")
                st = bd.find(NS + "strength")
                print("    %-11s %-28s binding %s -> %s" % (
                    parte, i,
                    st.get("value") if st is not None else "?",
                    vs.get("value") if vs is not None else "-"))
                achou = True
            if achou:
                break
        if not achou:
            print("    nenhum binding em Observation.code — CONFERIR A MAO")

    # ── 2. A copia do ValueSet, e se divergem entre pacotes ─────────────
    f_vs, vs = por_url(lista, "/BRTipoObservacao-1.0", "ValueSet")
    if vs is None:
        f_vs, vs = por_url(lista, "/BRTipoObservacao", "ValueSet")
    if vs is None:
        sys.exit("\n[2] ValueSet BRTipoObservacao: AUSENTE neste pacote.")
    sha = hashlib.sha256(open(f_vs, "rb").read()).hexdigest()
    print("\n[2] ValueSet %s v%s" % (valor(vs, "url"), valor(vs, "version")))
    print("    arquivo: %s" % os.path.relpath(f_vs))
    print("    sha256:  %s" % sha[:32])
    for outro in sorted(os.listdir(RAIZ)):
        if outro == pacote or not os.path.isdir(os.path.join(RAIZ, outro)):
            continue
        _d2, l2 = arquivos(outro)
        if not l2:
            continue
        f2, v2 = por_url(l2, "/BRTipoObservacao-1.0", "ValueSet")
        if v2 is None:
            f2, v2 = por_url(l2, "/BRTipoObservacao", "ValueSet")
        if v2 is None:
            continue
        s2 = hashlib.sha256(open(f2, "rb").read()).hexdigest()
        igual = "IDENTICO" if s2 == sha else "DIVERGE (defeito no. 11)"
        print("    contra %-8s v%-8s %s" % (outro, valor(v2, "version"), igual))

    # ── 3. O catalogo ───────────────────────────────────────────────────
    print("\n[3] O que o catalogo teria")
    total = 0
    todos = []
    for inc in vs.iter(NS + "include"):
        sy = inc.find(NS + "system")
        sistema = sy.get("value") if sy is not None else "?"
        enumerados = [(c.find(NS + "code").get("value"),
                       (c.find(NS + "display").get("value")
                        if c.find(NS + "display") is not None else ""))
                      for c in inc.findall(NS + "concept")]
        if enumerados:
            fonte = "enumerados no ValueSet"
            codigos = enumerados
        else:
            nome = sistema.rsplit("/", 1)[-1]
            f_cs, cs = por_url(lista, "/" + nome, "CodeSystem")
            if cs is None:
                print("    %-62s CodeSystem AUSENTE" % sistema)
                continue
            fonte = "CodeSystem %s v%s" % (os.path.basename(f_cs), valor(cs, "version"))
            codigos = [(c.find(NS + "code").get("value"),
                        (c.find(NS + "display").get("value")
                         if c.find(NS + "display") is not None else ""))
                       for c in cs.iter(NS + "concept") if c.find(NS + "code") is not None]
        so_codigos = [c for c, _ in codigos]
        dup = sorted({c for c in so_codigos if so_codigos.count(c) > 1})
        distintos = len(set(so_codigos))
        print("    %-62s %3d codigos  (%s)" % (sistema, distintos, fonte))
        if dup:
            print("        AVISO: %d codigo(s) duplicado(s): %s" % (len(dup), ", ".join(dup)))
        total += distintos
        todos += [(sistema, c, dd) for c, dd in codigos]
    excl = [c.find(NS + "code").get("value") for e in vs.iter(NS + "exclude")
            for c in e.findall(NS + "concept")]
    if excl:
        print("    compose.exclude: %s" % ", ".join(excl))
        total -= len(excl)
    print("    TOTAL ACEITO: %d" % total)

    # ── 4. Sinonimos e codigos que nao sao medida ───────────────────────
    print("\n[4] A mesma medida, duas grafias oficiais no MESMO ValueSet")
    presentes = {c for _s, c, _d in todos}
    for medida, (a, b) in SINONIMOS.items():
        marca = "ambos presentes" if all(x in presentes for x in b.split(" / ") + [a]) \
            else "conferir"
        print("    %-26s %-6s  e  %-18s %s" % (medida, a, b, marca))
    print("\n    Nao e tabela de equivalencia clinica (ADR-05). E a constatacao de que o")
    print("    ValueSet aceita as duas, e que o artefato de referencia escolheu a LOINC.")

    print("\n[5] Codigos do ValueSet que NAO sao medida de valor")
    for s, c, dd in todos:
        if s.endswith("/BRTipoObservacao") and c in ("DSIA", "RECIDI", "DF", "IAC"):
            print("    %-6s %s" % (c, dd))
    print("    Sao observacoes DESCRITIVAS (BRObservacaoDescritiva), nao medidas. O mesmo")
    print("    ValueSet serve aos dois perfis — emitir IAC como 'medida' passa no binding.")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "rac")
