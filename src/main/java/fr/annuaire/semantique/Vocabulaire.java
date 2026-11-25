package fr.annuaire.semantique;

import java.text.Normalizer;
import java.util.Locale;

/** Les IRI utilisées dans la base RDF. */
public final class Vocabulaire {

    private Vocabulaire() {
    }

    public static final String ANN = "https://annuaire-semantique.example/onto#";
    public static final String ENTITE_BASE = "https://annuaire-semantique.example/entite/";

    /** Graphe des ontologies : domaines d'intérêt et leurs champs, sous forme de formes SHACL. */
    public static final String GRAPHE_ONTOLOGIES = "urn:annuaire:ontologies";
    /** Graphe des données : les entités. */
    public static final String GRAPHE_DONNEES = "urn:annuaire:donnees";

    public static final String PREFIXES = """
            PREFIX ann: <%s>
            PREFIX sh: <http://www.w3.org/ns/shacl#>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            PREFIX geo: <http://www.w3.org/2003/01/geo/wgs84_pos#>
            """.formatted(ANN);

    public static String domaine(String id) {
        return ANN + "domaine-" + id;
    }

    /** Forme SHACL qui décrit le champ (libellé, type, ordre). */
    public static String forme(String domaineId, String champId) {
        return ANN + "forme-" + domaineId + "-" + champId;
    }

    /** Propriété RDF qui porte la valeur du champ sur une entité. */
    public static String propriete(String champId) {
        return ANN + "champ-" + champId;
    }

    public static String entite(String id) {
        return ENTITE_BASE + id;
    }

    public static String idDepuisIri(String iri, String prefixe) {
        return iri.startsWith(prefixe) ? iri.substring(prefixe.length()) : iri;
    }

    /** « Année de création » devient « annee-de-creation » : sûr dans une IRI et dans un nom de champ d'index. */
    public static String identifiant(String libelle) {
        String sansAccent = Normalizer.normalize(libelle, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String id = sansAccent.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (id.isEmpty()) {
            throw new fr.annuaire.modele.Modele.DonneeRefusee("Le libellé doit contenir des lettres ou des chiffres.");
        }
        return id.length() > 60 ? id.substring(0, 60) : id;
    }
}
