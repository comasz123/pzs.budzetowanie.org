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
    private final String demoHost;
    private final boolean localhostAsDemo;

    public TenantHostService(OrganizationRepository organizationRepository,
                             @Value("${app.tenant.demo-host:ngo.budzetowanie.org}") String demoHost,
                             @Value("${app.tenant.localhost-as-demo:true}") boolean localhostAsDemo) {
        this.organizationRepository = organizationRepository;
        this.demoHost = demoHost.trim().toLowerCase(Locale.ROOT);
        this.localhostAsDemo = localhostAsDemo;
    }

    public Optional<Organization> resolveOrganization(String host) {
        String normalized = normalizeHost(host);

        Optional<Organization> byHost = organizationRepository.findByHost(normalized);
        if (byHost.isPresent()) {
            return byHost;
        }

        if (isLocalDevHost(normalized) && localhostAsDemo) {
            return organizationRepository.findByHost(demoHost);
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
