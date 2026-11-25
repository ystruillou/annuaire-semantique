package fr.annuaire.modele;

import java.util.List;
import java.util.Map;

/** Les objets échangés par l'API. Ils ne dépendent d'aucune technique de stockage. */
public final class Modele {

    private Modele() {
    }

    /** Type de valeur d'un champ, traduit en type XSD dans la forme SHACL. */
    public enum TypeChamp {
        TEXTE, NOMBRE, OUI_NON
    }

    /**
     * Un champ propre à un domaine d'intérêt. Il est créé par un administrateur, pas par un développeur.
     *
     * @param facette vrai si le champ doit servir de filtre dans la recherche et les widgets
     */
    public record Champ(String id, String libelle, TypeChamp type, boolean facette, int ordre) {
    }

    /** Un domaine d'intérêt (une filière, une thématique) et la liste de ses champs. */
    public record Domaine(String id, String libelle, List<Champ> champs) {
    }

    /**
     * Une entité (entreprise, laboratoire, organisme...).
     *
     * @param champs valeurs des champs de domaine, par identifiant de champ
     */
    public record Entite(
            String id,
            String nom,
            String siren,
            String commune,
            String codePostal,
            Double latitude,
            Double longitude,
            List<String> domaines,
            Map<String, Object> champs) {
    }

    public record NouveauDomaine(String libelle) {
    }

    public record NouveauChamp(String libelle, TypeChamp type, boolean facette) {
    }

    public record ValeurFacette(String valeur, long nombre) {
    }

    public record Facette(String champ, String libelle, List<ValeurFacette> valeurs) {
    }

    public record ResultatRecherche(long total, List<Entite> entites, List<Facette> facettes) {
    }

    /** Requête de recherche : texte libre, domaine, et filtres exacts par champ. */
    public record Recherche(String texte, String domaine, Map<String, String> filtres, int page, int taille) {
    }

    /** Signale une donnée refusée, avec un message destiné à l'utilisateur. */
    public static class DonneeRefusee extends RuntimeException {
        public DonneeRefusee(String message) {
            super(message);
        }
    }

    public static class Introuvable extends RuntimeException {
        public Introuvable(String message) {
            super(message);
        }
    }
}
