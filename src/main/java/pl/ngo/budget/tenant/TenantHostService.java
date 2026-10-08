package pl.ngo.budget.tenant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.ngo.budget.model.Organization;
import pl.ngo.budget.repository.OrganizationRepository;

import java.util.Locale;
import java.util.Optional;

@Service
public class TenantHostService {

    private final OrganizationRepository organizationRepository;
    private final String appHost;
    private final boolean allowLocalhost;

    public TenantHostService(OrganizationRepository organizationRepository,
                             @Value("${app.host:ngo.budzetowanie.org}") String host,
                             @Value("${app.allow-localhost:false}") boolean allowLocalhost) {
        this.organizationRepository = organizationRepository;
        this.appHost = host.trim().toLowerCase(Locale.ROOT);
        this.allowLocalhost = allowLocalhost;
    }

    public Optional<Organization> resolveOrganization(String requestHost) {
        String normalized = normalizeHost(requestHost);

        Optional<Organization> byHost = organizationRepository.findByHost(normalized);
        if (byHost.isPresent()) {
            return byHost;
        }

        if (isLocalDevHost(normalized) && allowLocalhost) {
            return organizationRepository.findByHost(appHost);
        }

        return Optional.empty();
    }

    public static String normalizeHost(String host) {
        if (host == null) {
            return "";
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        int colon = normalized.indexOf(':');
        if (colon > 0) {
            normalized = normalized.substring(0, colon);
        }
        return normalized;
    }

    private static boolean isLocalDevHost(String host) {
        return "localhost".equals(host) || "127.0.0.1".equals(host);
    }
}
