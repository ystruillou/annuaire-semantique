package fr.annuaire.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Protège les écritures de l'API.
 * <p>
 * Toute requête qui n'est pas une lecture (GET, HEAD, OPTIONS) doit porter l'en-tête
 * {@code Authorization: Bearer <jeton>}. Le navigateur n'ajoute jamais cet en-tête de lui-même :
 * un autre site ne peut donc pas déclencher une écriture à l'insu de l'administrateur.
 * Sans jeton configuré, toutes les écritures sont refusées.
 */
@Component
public class JetonAdministration extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JetonAdministration.class);
    private static final Set<String> LECTURES = Set.of("GET", "HEAD", "OPTIONS");

    private final byte[] jeton;

    public JetonAdministration(@Value("${annuaire.admin.jeton:}") String jeton) {
        this.jeton = jeton == null ? new byte[0] : jeton.strip().getBytes(StandardCharsets.UTF_8);
        if (this.jeton.length < 16) {
            log.warn("Jeton d'administration absent ou trop court (16 caractères au moins) : les écritures sont refusées.");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requete) {
        // Refus par défaut : toute requête qui n'est pas une lecture exige le jeton, quel que soit son chemin.
        // On ne cherche pas à reconnaître « /api/ » dans l'adresse : une adresse déformée (point-virgule,
        // encodage) pourrait être lue autrement ici que par le routage.
        // Seule exception, comparée à l'identique : l'API GraphQL, qui ne propose que des lectures.
        return LECTURES.contains(requete.getMethod())
                || ("POST".equals(requete.getMethod()) && "/graphql".equals(requete.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain suite)
            throws ServletException, IOException {
        String entete = requete.getHeader("Authorization");
        boolean valide = jeton.length >= 16 && entete != null && entete.startsWith("Bearer ")
                && MessageDigest.isEqual(jeton, entete.substring(7).strip().getBytes(StandardCharsets.UTF_8));
        if (!valide) {
            reponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            reponse.setHeader("WWW-Authenticate", "Bearer");
            reponse.setContentType("application/problem+json;charset=UTF-8");
            reponse.getWriter().write("{\"status\":401,\"detail\":\"Jeton d'administration requis.\"}");
            return;
        }
        suite.doFilter(requete, reponse);
    }
}
