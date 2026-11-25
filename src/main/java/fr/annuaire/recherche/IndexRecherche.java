package fr.annuaire.recherche;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import fr.annuaire.modele.Modele.Champ;
import fr.annuaire.modele.Modele.Domaine;
import fr.annuaire.modele.Modele.Entite;
import fr.annuaire.modele.Modele.Facette;
import fr.annuaire.modele.Modele.Recherche;
import fr.annuaire.modele.Modele.ResultatRecherche;
import fr.annuaire.modele.Modele.ValeurFacette;
import fr.annuaire.semantique.Entites;
import fr.annuaire.semantique.Ontologies;

/**
 * L'index Elasticsearch des entités.
 * <p>
 * La base RDF reste la référence ; l'index en est une copie faite pour chercher vite et compter
 * par facette. Il peut être reconstruit à tout moment à partir de la base.
 * Les champs de domaine ne sont pas connus d'avance : un gabarit dynamique indexe tout nouveau
 * champ texte à la fois en plein texte et en valeur exacte ({@code .brut}) pour les facettes.
 */
@Service
public class IndexRecherche {

    private static final Logger log = LoggerFactory.getLogger(IndexRecherche.class);

    private static final String STRUCTURE = """
            {
              "settings": {
                "analysis": { "analyzer": { "defaut_fr": { "type": "french" } } }
              },
              "mappings": {
                "dynamic_templates": [
                  { "textes": {
                      "path_match": "champs.*", "match_mapping_type": "string",
                      "mapping": { "type": "text", "analyzer": "defaut_fr",
                                   "fields": { "brut": { "type": "keyword", "ignore_above": 256 } } } } }
                ],
                "properties": {
                  "nom": { "type": "text", "analyzer": "defaut_fr", "fields": { "brut": { "type": "keyword" } } },
                  "siren": { "type": "keyword" },
                  "commune": { "type": "text", "fields": { "brut": { "type": "keyword" } } },
                  "codePostal": { "type": "keyword" },
                  "domaines": { "type": "keyword" },
                  "position": { "type": "geo_point" },
                  "champs": { "type": "object", "dynamic": true }
                }
              }
            }
            """;

    private final ElasticsearchClient es;
    private final String index;
    private final Entites entites;
    private final Ontologies ontologies;

    public IndexRecherche(@Value("${annuaire.elasticsearch.url}") String url,
                          @Value("${annuaire.elasticsearch.index}") String index,
                          Entites entites, Ontologies ontologies) {
        this.es = ElasticsearchClient.of(b -> b.host(url));
        this.index = index;
        this.entites = entites;
        this.ontologies = ontologies;
    }

    /** Vide l'index et le remplit à partir de la base RDF. Renvoie le nombre d'entités indexées. */
    public int reconstruire() {
        try {
            if (es.indices().exists(e -> e.index(index)).value()) {
                es.indices().delete(d -> d.index(index));
            }
            es.indices().create(c -> c.index(index).withJson(new StringReader(STRUCTURE)));
            List<Entite> toutes = entites.toutes();
            if (!toutes.isEmpty()) {
                es.bulk(b -> {
                    toutes.forEach(e -> b.operations(o -> o.index(i -> i.index(index).id(e.id()).document(document(e)))));
                    return b.refresh(co.elastic.clients.elasticsearch._types.Refresh.True);
                });
            }
            log.info("Index « {} » reconstruit : {} entités", index, toutes.size());
            return toutes.size();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void indexer(Entite e) {
        try {
            assurerIndex();
            es.index(i -> i.index(index).id(e.id()).document(document(e))
                    .refresh(co.elastic.clients.elasticsearch._types.Refresh.True));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public void retirer(String id) {
        try {
            assurerIndex();
            es.delete(d -> d.index(index).id(id).refresh(co.elastic.clients.elasticsearch._types.Refresh.True));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public ResultatRecherche chercher(Recherche r) {
        int taille = Math.max(1, Math.min(r.taille() <= 0 ? 20 : r.taille(), 200));
        int page = Math.max(0, r.page());
        Domaine domaine = vide(r.domaine()) ? null : ontologies.exiger(r.domaine());

        // Facettes : la commune, toujours ; les champs marqués « facette » quand un domaine est choisi.
        Map<String, String> libelles = new LinkedHashMap<>();
        Map<String, String> champsIndex = new LinkedHashMap<>();
        libelles.put("commune", "Commune");
        champsIndex.put("commune", "commune.brut");
        if (domaine != null) {
            for (Champ c : domaine.champs()) {
                if (c.facette()) {
                    libelles.put(c.id(), c.libelle());
                    champsIndex.put(c.id(), "champs." + c.id() + ".brut");
                }
            }
        }

        BoolQuery.Builder filtre = new BoolQuery.Builder();
        if (vide(r.texte())) {
            filtre.must(m -> m.matchAll(a -> a));
        } else {
            filtre.must(m -> m.simpleQueryString(s -> s.query(r.texte())
                    .fields("nom^3", "commune", "siren", "champs.*")
                    .defaultOperator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.And)
                    .lenient(true)));
        }
        if (domaine != null) {
            filtre.filter(f -> f.term(t -> t.field("domaines").value(domaine.id())));
        }
        if (r.filtres() != null) {
            r.filtres().forEach((champ, valeur) -> {
                String champIndex = champsIndex.get(champ);
                if (champIndex != null && !vide(valeur)) {
                    filtre.filter(f -> f.term(t -> t.field(champIndex).value(valeur)));
                }
            });
        }

        try {
            assurerIndex();
            SearchResponse<Map> reponse = es.search(s -> {
                s.index(index).query(q -> q.bool(filtre.build())).from(page * taille).size(taille)
                        .trackTotalHits(t -> t.enabled(true));
                if (vide(r.texte())) {
                    s.sort(o -> o.field(f -> f.field("nom.brut")));
                }
                champsIndex.forEach((id, champ) -> s.aggregations(id, a -> a.terms(t -> t.field(champ).size(30))));
                return s;
            }, Map.class);

            List<Entite> trouvees = new ArrayList<>();
            for (Hit<Map> hit : reponse.hits().hits()) {
                trouvees.add(entite(hit.id(), hit.source()));
            }
            List<Facette> facettes = new ArrayList<>();
            libelles.forEach((id, libelle) -> {
                Aggregate agregat = reponse.aggregations().get(id);
                List<ValeurFacette> valeurs = new ArrayList<>();
                if (agregat != null && agregat.isSterms()) {
                    agregat.sterms().buckets().array()
                            .forEach(b -> valeurs.add(new ValeurFacette(b.key().stringValue(), b.docCount())));
                }
                if (!valeurs.isEmpty()) {
                    facettes.add(new Facette(id, libelle, valeurs));
                }
            });
            long total = reponse.hits().total() == null ? trouvees.size() : reponse.hits().total().value();
            return new ResultatRecherche(total, trouvees, facettes);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void assurerIndex() throws IOException {
        if (!es.indices().exists(e -> e.index(index)).value()) {
            reconstruire();
        }
    }

    private static Map<String, Object> document(Entite e) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("nom", e.nom());
        d.put("siren", e.siren());
        d.put("commune", e.commune());
        d.put("codePostal", e.codePostal());
        d.put("domaines", e.domaines());
        if (e.latitude() != null && e.longitude() != null) {
            d.put("position", Map.of("lat", e.latitude(), "lon", e.longitude()));
        }
        d.put("champs", e.champs());
        return d;
    }

    @SuppressWarnings("unchecked")
    private static Entite entite(String id, Map<?, ?> source) {
        Map<String, Object> s = (Map<String, Object>) source;
        Map<String, Object> position = (Map<String, Object>) s.get("position");
        Object domaines = s.get("domaines");
        Object champs = s.get("champs");
        return new Entite(id,
                (String) s.get("nom"), (String) s.get("siren"), (String) s.get("commune"), (String) s.get("codePostal"),
                position == null ? null : ((Number) position.get("lat")).doubleValue(),
                position == null ? null : ((Number) position.get("lon")).doubleValue(),
                domaines instanceof List<?> l ? l.stream().map(Object::toString).toList() : List.of(),
                champs instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of());
    }

    private static boolean vide(String s) {
        return s == null || s.isBlank();
    }
}
