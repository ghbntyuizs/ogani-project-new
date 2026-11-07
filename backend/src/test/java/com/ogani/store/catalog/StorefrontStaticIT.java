package com.ogani.store.catalog;

import com.ogani.store.common.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import java.util.List;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

class StorefrontStaticIT extends AbstractIntegrationTest {
    @Test void shopEntryRedirectsToPublicStorefront() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/shop")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/shop/index.html"));
    }
    @Test void publicPagesAndModulesArePackagedWithoutMakingCartApiPublic() throws Exception {
        for(String file:List.of("index.html","product.html","cart.html","addresses.html","checkout.html","orders.html","order.html","login.html","register.html","forgot-password.html"))
            mvc.perform(MockMvcRequestBuilders.get("/shop/"+file)).andExpect(status().isOk()).andExpect(content().string(containsString("assets/app.js")));
        mvc.perform(MockMvcRequestBuilders.get("/shop/assets/api.js")).andExpect(status().isOk()).andExpect(content().string(containsString("createApi")));
        mvc.perform(MockMvcRequestBuilders.get("/api/cart")).andExpect(status().isUnauthorized());
    }
    @Test void testsAndDevelopmentServerAreExcludedFromDeployedResources() throws Exception {
        for(String path:List.of("tests/api.test.mjs","package.json","dev-server.mjs"))mvc.perform(MockMvcRequestBuilders.get("/shop/"+path)).andExpect(status().isNotFound());
    }
}
