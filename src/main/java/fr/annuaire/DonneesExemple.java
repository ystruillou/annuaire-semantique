package fr.annuaire;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import fr.annuaire.modele.Modele.Entite;
import fr.annuaire.modele.Modele.NouveauChamp;
import fr.annuaire.modele.Modele.TypeChamp;
import fr.annuaire.recherche.IndexRecherche;
import fr.annuaire.semantique.Entites;
import fr.annuaire.semantique.Ontologies;
import fr.annuaire.widget.Widgets;
import fr.annuaire.widget.Widgets.NouveauWidget;

/**
 * Remplit une base vide avec un domaine, des entités fictives et deux widgets, pour la démonstration.
 * Activé par {@code annuaire.exemple=true}. Les sociétés sont inventées ; seules les communes existent.
 */
@Component
@ConditionalOnProperty(name = "annuaire.exemple", havingValue = "true")
public class DonneesExemple implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DonneesExemple.class);

    private final Ontologies ontologies;
    private final Entites entites;
    private final IndexRecherche index;
    private final Widgets widgets;

    public DonneesExemple(Ontologies ontologies, Entites entites, IndexRecherche index, Widgets widgets) {
        this.ontologies = ontologies;
        this.entites = entites;
        this.index = index;
        this.widgets = widgets;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!ontologies.domaines().isEmpty()) {
            return;
        }
        String d = ontologies.creerDomaine("Nautisme").id();
        ontologies.ajouterChamp(d, new NouveauChamp("Activité", TypeChamp.TEXTE, true));
        ontologies.ajouterChamp(d, new NouveauChamp("Effectif", TypeChamp.NOMBRE, false));
        ontologies.ajouterChamp(d, new NouveauChamp("Exporte", TypeChamp.OUI_NON, false));
        String activite = d + "-activite";
        String effectif = d + "-effectif";
        String exporte = d + "-exporte";

        creer("Chantier Exemple de l'Odet", "Quimper", "29000", 47.9960, -4.1024, d,
                Map.of(activite, "Construction navale", effectif, "34", exporte, "true"));
        creer("Voilerie Fictive du Ponant", "Brest", "29200", 48.3904, -4.4861, d,
                Map.of(activite, "Voilerie", effectif, "12", exporte, "false"));
        creer("Composites Démonstration", "Lorient", "56100", 47.7483, -3.3702, d,
                Map.of(activite, "Matériaux composites", effectif, "58", exporte, "true"));
        creer("Atelier Modèle des Glénan", "Concarneau", "29900", 47.8736, -3.9179, d,
                Map.of(activite, "Construction navale", effectif, "9", exporte, "false"));
        creer("Électronique Marine Témoin", "Saint-Malo", "35400", 48.6493, -2.0257, d,
                Map.of(activite, "Électronique embarquée", effectif, "21", exporte, "true"));
        creer("Accastillage Spécimen", "Vannes", "56000", 47.6582, -2.7608, d,
                Map.of(activite, "Accastillage", effectif, "15", exporte, "false"));
        creer("Voilerie Essai d'Armor", "Paimpol", "22500", 48.7785, -3.0440, d,
                Map.of(activite, "Voilerie", effectif, "6", exporte, "false"));
        creer("Bureau d'études Maquette", "Lorient", "56100", 47.7290, -3.3660, d,
                Map.of(activite, "Architecture navale", effectif, "11", exporte, "true"));

        index.reconstruire();
        if (widgets.tous().isEmpty()) {
            widgets.creer(new NouveauWidget("Annuaire du nautisme", "annuaire", d, List.of(activite, effectif), List.of("*")));
            widgets.creer(new NouveauWidget("Carte du nautisme", "carte", d, List.of(activite), List.of("*")));
        }
        log.info("Données d'exemple créées : 1 domaine, 8 entités, 2 widgets.");
    }

    private void creer(String nom, String commune, String codePostal, double lat, double lon,
                       String domaine, Map<String, Object> champs) {
        entites.enregistrer(new Entite(null, nom, null, commune, codePostal, lat, lon, List.of(domaine), champs));
    }
}
