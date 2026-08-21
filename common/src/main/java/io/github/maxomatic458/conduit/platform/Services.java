package io.github.maxomatic458.conduit.platform;

import io.github.maxomatic458.conduit.Constants;
import io.github.maxomatic458.conduit.platform.services.IConfigService;
import io.github.maxomatic458.conduit.platform.services.IPlatformHelper;

import java.util.ServiceLoader;

public class Services {
    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);
    public static final IConfigService CONFIG = load(IConfigService.class);

    // load a service for the current environment
    public static <T> T load(Class<T> clazz) {

        final T loadedService = ServiceLoader.load(clazz, Services.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
        Constants.LOG.debug("Loaded {} for service {}", loadedService, clazz);
        return loadedService;
    }
}