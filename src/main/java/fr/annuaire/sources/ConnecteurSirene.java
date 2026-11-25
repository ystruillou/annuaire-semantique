package fr.annuaire.sources;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import fr.annuaire.modele.Modele.DonneeRefusee;
import fr.annuaire.modele.Modele.Entite;

/**
 * Connecteur vers l'API publique « Recherche d'entreprises » de l'État, qui expose le répertoire Sirene.
 * Il sert à préremplir une entité à partir d'un nom ou d'un numéro SIREN.
 * L'API est ouverte, sans clé ; elle limite le nombre d'appels par seconde.
 */
@Service
public class ConnecteurSirene {

    private final RestClient http;

    public ConnecteurSirene(@Value("${annuaire.sirene.url}") String url) {
        this.http = RestClient.builder().baseUrl(url).build();
    }

    /** Renvoie des entités proposées, non enregistrées (sans identifiant). */
    @SuppressWarnings("unchecked")
    public List<Entite> chercher(String texte) {
        if (texte == null || texte.strip().length() < 3) {
            throw new DonneeRefusee("Saisissez au moins 3 caractères : un nom ou un numéro SIREN.");
        }
        Map<String, Object> reponse;
        try {
            reponse = http.get()
                    .uri(u -> u.path("/search").queryParam("q", texte.strip()).queryParam("per_page", 10).build())
                    .retrieve().body(Map.class);
        } catch (RestClientException e) {
            throw new DonneeRefusee("Le service Sirene ne répond pas pour le moment.");
        }
        List<Entite> propositions = new ArrayList<>();
        Object resultats = reponse == null ? null : reponse.get("results");
        if (resultats instanceof List<?> liste) {
            for (Object o : liste) {
                Map<String, Object> r = (Map<String, Object>) o;
                Map<String, Object> siege = r.get("siege") instanceof Map<?, ?> s ? (Map<String, Object>) s : Map.of();
                propositions.add(new Entite(
                        null,
                        texte(r.get("nom_complet")),
                        texte(r.get("siren")),
                        texte(siege.get("libelle_commune")),
                        texte(siege.get("code_postal")),
                        nombre(siege.get("latitude")),
                        nombre(siege.get("longitude")),
                        List.of(),
                        Map.of()));
            }
        }
        return propositions;
    }

    private static String texte(Object o) {
        return o == null ? null : o.toString();
    }

    private static Double nombre(Object o) {
        try {
            return o == null ? null : Double.valueOf(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
