package com.example.apresentacao.services;


import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class JWTCertificateService{


    @Autowired
    private CertificateService certificateService;

    @Autowired
    private ResourceLoader resourceLoader;

    @Value("${jwt.certificate.x5t}")
    private String x5t;

    @Value("${jwt.certificate.client-app-id}")
    private String clientAppId;

    @Value("${jwt.certificate.classpath}")
    private String classpath;

    @Value("${azure.auth.url}")
    private String azuerAuthURL;

    @Value("${jwt.certificate.tenant}")
    private String tenant;

    @Value("${jwt.certificate.scope}")
    private String scope;





    public String getAssertion() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {

        String uuid = UUID.randomUUID().toString();

        /********* Processo abaixo le o certificado do caminho classpath  ***********/
        Resource resource = resourceLoader.getResource(classpath);

        // Le o certificado e retorna um objeto do tipo RSAPrivateKey que é usado para assinar o JWT token abaixo
        RSAPrivateKey rsaPrivateKey = certificateService.readPrivateKey(resource.getInputStream());


        // Cria o token JWT com os dados abaixo
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.RS256; // Algoritmo de assinatura do JWT
        LocalDateTime now = LocalDateTime.now(); // Data e hora atual
        ZoneId zoneId = ZoneId.systemDefault(); // Timezone do sistema

        long nbf = now.atZone(zoneId).toEpochSecond(); // Data e hora atual em segundos

        LocalDateTime now2 = now.plusSeconds(30); // Data e hora atual + 30 segundos
        long exp = now2.atZone(zoneId).toEpochSecond(); //

        String aud = azuerAuthURL + tenant + "/v2.0";
        return Jwts.builder()
                .setHeaderParam("x5t", x5t)
                .setHeaderParam("typ", "JWT")
                .claim("iss", clientAppId)
                .claim("aud", aud)
                .claim("sub", clientAppId)
                .claim("nbf", nbf)
                .claim("exp", exp)
                .claim("jti", uuid)
                .signWith(signatureAlgorithm, rsaPrivateKey)
                .compact();
    }


    /* Abaixo é feito o post para o Azure AD para obter o token JWT */
    public Mono<String> getToken() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {

        WebClient client =  WebClient.create(azuerAuthURL);

        return client.post()
                .uri(tenant+"/oauth2/v2.0/token")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .body(BodyInserters
                        .fromFormData("client_id", clientAppId)
                        .with("scope", scope)
                        .with("grant_type", "client_credentials")
                        .with("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                        .with("signature_algorithm", "RS256")
                        .with("client_assertion", this.getAssertion())
                )
                .retrieve()
                .bodyToMono(String.class);

    }
}