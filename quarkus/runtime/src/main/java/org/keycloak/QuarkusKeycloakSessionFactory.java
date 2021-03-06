package org.keycloak;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jboss.logging.Logger;
import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.ProviderManagerRegistry;
import org.keycloak.provider.Spi;
import org.keycloak.services.DefaultKeycloakSessionFactory;
import org.keycloak.services.resources.admin.permissions.AdminPermissions;

public final class QuarkusKeycloakSessionFactory extends DefaultKeycloakSessionFactory {

    private static final Logger logger = Logger.getLogger(QuarkusKeycloakSessionFactory.class);

    public static QuarkusKeycloakSessionFactory getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new QuarkusKeycloakSessionFactory();
        }

        return INSTANCE;
    }

    public static void setInstance(QuarkusKeycloakSessionFactory instance) {
        INSTANCE = instance;
    }

    private static QuarkusKeycloakSessionFactory INSTANCE;
    private final Boolean reaugmented;
    private final Map<Spi, Map<Class<? extends Provider>, Map<String, Class<? extends ProviderFactory>>>> factories;

    public QuarkusKeycloakSessionFactory(
            Map<Spi, Map<Class<? extends Provider>, Map<String, Class<? extends ProviderFactory>>>> factories,
            Map<Class<? extends Provider>, String> defaultProviders,
            Boolean reaugmented) {
        this.provider = defaultProviders;
        this.factories = factories;
        this.reaugmented = reaugmented;
    }

    private QuarkusKeycloakSessionFactory() {
        reaugmented = false;
        factories = Collections.emptyMap();
    }

    @Override
    public void init() {
        serverStartupTimestamp = System.currentTimeMillis();
        spis = factories.keySet();

        for (Spi spi : spis) {
            for (Map<String, Class<? extends ProviderFactory>> factoryClazz : factories.get(spi).values()) {
                for (Map.Entry<String, Class<? extends ProviderFactory>> entry : factoryClazz.entrySet()) {
                    ProviderFactory factory = lookupProviderFactory(entry.getValue());
                    Config.Scope scope = Config.scope(spi.getName(), factory.getId());

                    factory.init(scope);
                    factoriesMap.computeIfAbsent(spi.getProviderClass(), k -> new HashMap<>()).put(factory.getId(), factory);
                }
            }
        }

        for (Map<String, ProviderFactory> f : factoriesMap.values()) {
            for (ProviderFactory factory : f.values()) {
                factory.postInit(this);
            }
        }

        AdminPermissions.registerListener(this);
        // make the session factory ready for hot deployment
        ProviderManagerRegistry.SINGLETON.setDeployer(this);
    }

    private ProviderFactory lookupProviderFactory(Class<? extends ProviderFactory> factoryClazz) {
        ProviderFactory factory;

        try {
            factory = factoryClazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return factory;
    }
}
