package io.github.keveon.rendoc.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.keveon.rendoc.config.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class BearerTokenFilterTest {

    private final BearerTokenFilter filter =
            new BearerTokenFilter(new SecurityProperties("test-secret-token"));

    @Test
    void allowsAValidBearerToken() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/convert");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer test-secret-token");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
    }

    @Test
    void rejectsMissingCredentialsWithAProblemResponse() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/convert");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo("Bearer realm=\"rendoc\"");
        assertThat(response.getContentAsString())
                .contains("\"code\":\"authentication_required\"");
    }

    @Test
    void doesNotProtectHealthEndpoints() throws Exception {
        var request = new MockHttpServletRequest("GET", "/readyz");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/actuator/health",
        "/actuator/health/readiness",
        "/actuator/info",
        "/actuator/prometheus"
    })
    void protectsActuatorEndpointsByDefault(String path) throws Exception {
        var request = new MockHttpServletRequest("GET", path);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void protectsTheApiWhenTheApplicationUsesAContextPath() throws Exception {
        var request = new MockHttpServletRequest("POST", "/rendoc/api/v1/convert");
        request.setContextPath("/rendoc");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void protectsMatrixParameterVariantsOfTheApiPath() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1;ignored/convert");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void protectsUnknownRoutesByDefault() throws Exception {
        var request = new MockHttpServletRequest("GET", "/future-api");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
    }
}
