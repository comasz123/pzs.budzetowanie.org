package pl.ngo.budget.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.ngo.budget.entity.cost.Employee;
import pl.ngo.budget.entity.cost.Expenditure;
import pl.ngo.budget.entity.coverage.BudgetItemTemplate;
import pl.ngo.budget.entity.coverage.Grant;
import pl.ngo.budget.entity.coverage.GrantBudgetItem;
import pl.ngo.budget.entity.coverage.PlannedEvent;
import pl.ngo.budget.entity.coverage.Project;
import pl.ngo.budget.entity.coverage.Publication;
import pl.ngo.budget.entity.coverage.Sponsor;
import pl.ngo.budget.entity.coverage.SponsorContact;
import pl.ngo.budget.entity.coverage.TravelBudgetLine;
import pl.ngo.budget.model.Organization;
import pl.ngo.budget.model.Role;
import pl.ngo.budget.model.User;
import pl.ngo.budget.repository.BudgetItemTemplateRepository;
import pl.ngo.budget.repository.EmployeeRepository;
import pl.ngo.budget.repository.ExpenditureAllocationRepository;
import pl.ngo.budget.repository.ExpenditureRepository;
import pl.ngo.budget.repository.GrantCoverageRepository;
import pl.ngo.budget.repository.GrantRepository;
import pl.ngo.budget.repository.OrganizationRepository;
import pl.ngo.budget.repository.PlannedEventRepository;
import pl.ngo.budget.service.BudgetSetupService;
import pl.ngo.budget.repository.CostAllocationRepository;
import pl.ngo.budget.repository.ProjectRepository;
import pl.ngo.budget.repository.PublicationRepository;
import pl.ngo.budget.repository.RoleRepository;
import pl.ngo.budget.repository.SponsorContactRepository;
import pl.ngo.budget.repository.SponsorRepository;
import pl.ngo.budget.repository.TravelBudgetLineRepository;
import pl.ngo.budget.repository.UserRepository;
import pl.ngo.budget.security.AppRoles;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Order(0)
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final int FISCAL_YEAR_2025 = 2025;
    private static final int FISCAL_YEAR_2026 = 2026;

    private static final String DEMO_ORG_CODE = "green-future";
    private static final String DEMO_ORG_HOST = "ngo.budzetowanie.org";

    private static final String PERSONNEL_TEMPLATE_CODE = "KOSZT_PER";
    private static final String ADMIN_TEMPLATE_CODE = "KOSZT_ADM";

    private static final String EVENTS_TEMPLATE_CODE = "KOSZT_KSW";

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmployeeRepository employeeRepository;
    private final SponsorRepository sponsorRepository;
    private final SponsorContactRepository sponsorContactRepository;
    private final ProjectRepository projectRepository;
    private final BudgetItemTemplateRepository budgetItemTemplateRepository;
    private final GrantRepository grantRepository;
    private final GrantCoverageRepository grantCoverageRepository;
    private final ExpenditureAllocationRepository expenditureAllocationRepository;
    private final ExpenditureRepository expenditureRepository;
    private final PublicationRepository publicationRepository;
    private final PlannedEventRepository plannedEventRepository;
    private final TravelBudgetLineRepository travelBudgetLineRepository;
    private final CostAllocationRepository costAllocationRepository;
    private final BudgetSetupService budgetSetupService;
    private final PasswordEncoder passwordEncoder;

    private static final String EVENTS_TEMPLATE_NAME = "Konferencje, spotkania, warsztaty";

    private static final String TRAVEL_TEMPLATE_CODE = "KOSZT_LOG";
    private static final String TRAVEL_TEMPLATE_NAME = "Podróże";

    private static final String PUBLICATIONS_TEMPLATE_CODE = "KOSZT_MAT";
    private static final String PROMOTION_TEMPLATE_CODE = "KOSZT_PROM";
    private static final String EQUIPMENT_TEMPLATE_CODE = "KOSZT_SPRZ";

    private static final String[][] EMPLOYEE_SEED = {
            {"Jan", "Kowalski", "Dyrektor", "jan.kowalski@ngo.pl", "+48 501 111 222", "144000.00"},
            {"Anna", "Nowak", "Księgowa", "anna.nowak@ngo.pl", "+48 502 222 333", "96000.00"},
            {"Piotr", "Wiśniewski", "Specjalista ds. Edukacji", "piotr.wisniewski@ngo.pl", "+48 503 333 444", "108000.00"},
            {"Marta", "Wójcik", "Menedżer PR i Marketingu", "marta.wojcik@ngo.pl", "+48 504 444 555", "114000.00"},
            {"Tomasz", "Zieliński", "Kierownik Biura", "tomasz.zielinski@ngo.pl", "+48 505 555 666", "84000.00"},
            {"System", "Organizacji", "Opłaty zewnętrzne", "system@ngo.pl", "", "0.00"}
    };

    @Value("${app.admin.email:admin@ngo.pl}")
    private String adminEmail;

    @Value("${app.admin.password:admin123}")
    private String adminPassword;

    @Value("${app.admin.first-name:Admin}")
    private String adminFirstName;

    @Value("${app.admin.last-name:Systemowy}")
    private String adminLastName;

    @Value("${app.editor.email:katarzynadaliga@zielonasiec.pl}")
    private String editorEmail;

    @Value("${app.editor.password:}")
    private String editorPassword;

    @Value("${app.editor.first-name:Katarzyna}")
    private String editorFirstName;

    @Value("${app.editor.last-name:Daliga}")
    private String editorLastName;

    @Value("${app.seed.overwrite-existing:false}")
    private boolean overwriteExisting;

    /** false = pusta instalacja: organizacja, role, admin i kategorie budżetowe, bez danych demo. */
    @Value("${app.seed.demo-data:true}")
    private boolean seedDemoData;

    @Value("${app.org.code:pzs}")
    private String emptyOrgCode;

    @Value("${app.org.name:PZS}")
    private String emptyOrgName;

    @Value("${app.org.legal-name:PZS}")
    private String emptyOrgLegalName;

    @Value("${app.tenant.demo-host:ngo.budzetowanie.org}")
    private String demoOrgHost;

    @Value("${app.budget.clear-2025-on-startup:false}")
    private boolean clear2025OnStartup;

    @Value("${app.budget.clear-2026-on-startup:false}")
    private boolean clear2026OnStartup;

    @Value("${app.budget.allow-destructive-clear:false}")
    private boolean allowDestructiveClear;

    public DataInitializer(OrganizationRepository organizationRepository,
                           UserRepository userRepository,
                           RoleRepository roleRepository,
                           EmployeeRepository employeeRepository,
                           SponsorRepository sponsorRepository,
                           SponsorContactRepository sponsorContactRepository,
                           ProjectRepository projectRepository,
                           BudgetItemTemplateRepository budgetItemTemplateRepository,
                           GrantRepository grantRepository,
                           GrantCoverageRepository grantCoverageRepository,
                           ExpenditureAllocationRepository expenditureAllocationRepository,
                           ExpenditureRepository expenditureRepository,
                           PublicationRepository publicationRepository,
                           PlannedEventRepository plannedEventRepository,
                           TravelBudgetLineRepository travelBudgetLineRepository,
                           CostAllocationRepository costAllocationRepository,
                           BudgetSetupService budgetSetupService,
                           PasswordEncoder passwordEncoder) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.employeeRepository = employeeRepository;
        this.sponsorRepository = sponsorRepository;
        this.sponsorContactRepository = sponsorContactRepository;
        this.projectRepository = projectRepository;
        this.budgetItemTemplateRepository = budgetItemTemplateRepository;
        this.grantRepository = grantRepository;
        this.grantCoverageRepository = grantCoverageRepository;
        this.expenditureAllocationRepository = expenditureAllocationRepository;
        this.expenditureRepository = expenditureRepository;
        this.publicationRepository = publicationRepository;
        this.plannedEventRepository = plannedEventRepository;
        this.travelBudgetLineRepository = travelBudgetLineRepository;
        this.costAllocationRepository = costAllocationRepository;
        this.budgetSetupService = budgetSetupService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Role adminRole = ensureRole("ROLE_ADMIN", "Administrator");
        Role editRole = ensureRole("ROLE_EDIT", "Edycja");
        ensureRole("ROLE_READ_ONLY", "Podgląd");
        ensureRole("ROLE_FINANCE", "Skarbnik / Księgowość");
        ensureRole("ROLE_USER", "Koordynator projektu");
        if (!seedDemoData) {
            runEmptyInstall(adminRole, editRole);
            return;
        }

        Organization demoOrganization = ensureDemoOrganization();
        syncAdminUser(adminRole, demoOrganization);
        syncEditorUser(editRole, demoOrganization);
        syncEmployeesFromSeed();
        backfillExpenditureFiscalYears();
        syncGrantDisplayNames();
        ensureEmployeesFromSeedExist();
        ensureAllStandardBudgetTemplates();
        budgetSetupService.ensureBudgetCategoryDisplayOrder();

        removeBudgetDataIfConfigured();

        if (!clear2025OnStartup) {
            ensureDemoProjectsAndGrants2025();
        }
        if (!clear2026OnStartup) {
            ensureDemoProjectsAndGrants2026();
        }
        ensureAllGrantBudgetItems();

        if (!clear2025OnStartup || !clear2026OnStartup) {
            List<Grant> existingGrants = grantRepository.findAll();
            if (!existingGrants.isEmpty()) {
                seedPublicationsIfMissing(existingGrants);
                seedPlannedEventsIfMissing(existingGrants);
                seedTravelLinesIfMissing(existingGrants);
            }
        }

        log.info(
                "Stan danych: szablony={}, alokacje 2025={}, wydatki 2025={}, alokacje 2026={}, wydatki 2026={}, publikacje={}, granty={}",
                budgetItemTemplateRepository.count(),
                costAllocationRepository.countByFiscalYear(FISCAL_YEAR_2025),
                expenditureRepository.countByFiscalYear(FISCAL_YEAR_2025),
                costAllocationRepository.countByFiscalYear(FISCAL_YEAR_2026),
                expenditureRepository.countByFiscalYear(FISCAL_YEAR_2026),
                publicationRepository.count(),
                grantRepository.count()
        );

        if (grantRepository.count() > 0) {
            return;
        }

        // 1. ROLE (pozostałe)
        Role userRole = ensureRole("ROLE_USER", "Koordynator projektu");
        ensureRole("ROLE_READ_ONLY", "Podgląd i audyt");

        // 2. ADMIN — już zsynchronizowany przed guardem

        // 3. 5 UŻYTKOWNIKÓW
        String sharedPassword = passwordEncoder.encode("Password123!");
        List<User> users = new ArrayList<>();

        String[][] userData = {
            {"jan.kowalski@ngo.pl", "Jan", "Kowalski"},
            {"anna.nowak@ngo.pl", "Anna", "Nowak"},
            {"piotr.wisniewski@ngo.pl", "Piotr", "Wiśniewski"},
            {"marta.wojcik@ngo.pl", "Marta", "Wójcik"},
            {"tomasz.zielinski@ngo.pl", "Tomasz", "Zieliński"}
        };

        for (String[] uData : userData) {
            if (userRepository.findByEmail(uData[0]).isEmpty()) {
                User u = new User();
                u.setEmail(uData[0]);
                u.setPassword(sharedPassword);
                u.setFirstName(uData[1]);
                u.setLastName(uData[2]);
                u.setRoles(Set.of(userRole));
                users.add(u);
            }
        }
        if (!users.isEmpty()) {
            userRepository.saveAll(users);
        }

        // 4. 5 PRACOWNIKÓW (zsynchronizowani wcześniej przez syncEmployeesFromSeed)
        List<Employee> employees = employeeRepository.findAll();

        // 5. 8 SPONSORÓW
        List<Sponsor> sponsors;
        if (sponsorRepository.count() == 0) {
            List<Sponsor> toSave = new ArrayList<>();
            String[][] spData = {
                {"Fundacja Rozwoju Demokracji", "GRANT", "NIP 5210001122, Warszawa"},
                {"Narodowy Instytut Wolności", "GOVERNMENT", "Program PROO, Warszawa"},
                {"Ministerstwo Kultury i Dziedzictwa", "GOVERNMENT", "Programy Edukacyjne"},
                {"Urząd Marszałkowski Województwa", "LOCAL_GOVT", "Dotacje regionalne"},
                {"TechCorp Polska Sp. z o.o.", "CORPORATE", "Sponsoring CSR"},
                {"Bank Ochrony Środowiska", "CORPORATE", "Dotacje Ekologiczne"},
                {"Polsko-Amerykańska Fundacja Wolności", "GRANT", "Programy Polsko-Amerykańskie"},
                {"Darczyńcy Prywatni - Fundusz NGO", "DONATION", "Zbiórki i wpłaty indywidualne"}
            };
            for (String[] s : spData) {
                Sponsor sp = new Sponsor();
                sp.setName(s[0]);
                sp.setType(s[1]);
                sp.setNotes(s[2]);
                sp.setCity("Warszawa");
                sp.setCountry("PL");
                toSave.add(sp);
            }
            sponsors = sponsorRepository.saveAll(toSave);
        } else {
            sponsors = sponsorRepository.findAll();
        }

        // 6. 15 OSÓB KONTAKTOWYCH
        if (sponsorContactRepository.count() == 0 && !sponsors.isEmpty()) {
            List<SponsorContact> contacts = new ArrayList<>();
            String[][] scData = {
                {"Katarzyna", "Lewandowska", "k.lewandowska@frd.org.pl", "+48 22 100 20 30", "Główny opiekun grantu"},
                {"Marek", "Dąbrowski", "m.dabrowski@frd.org.pl", "+48 22 100 20 31", "Specjalista ds. rozliczeń"},
                {"Ewa", "Kamińska", "ewa.kaminska@niw.gov.pl", "+48 22 600 30 40", "Koordynator PROO"},
                {"Robert", "Szymański", "r.szymanski@niw.gov.pl", "+48 22 600 30 41", "Weryfikator finansowy"},
                {"Magdalena", "Wozniak", "mwozniak@kultura.gov.pl", "+48 22 800 40 50", "Departament Edukacji"},
                {"Paweł", "Kozłowski", "pkozlowski@kultura.gov.pl", "+48 22 800 40 51", "Departament Finansów"},
                {"Agnieszka", "Jankowska", "a.jankowska@um.gov.pl", "+48 12 300 50 60", "Oddział NGO Kraków"},
                {"Michał", "Mazur", "m.mazur@um.gov.pl", "+48 12 300 50 61", "Główny księgowy dotacji"},
                {"Karolina", "Wojciechowska", "csr@techcorp.pl", "+48 22 400 60 70", "Menedżer CSR"},
                {"Grzegorz", "Krawczyk", "g.krawczyk@techcorp.pl", "+48 22 400 60 71", "Dział prawny i umów"},
                {"Sylwia", "Kaczmarek", "s.kaczmarek@bos.pl", "+48 22 500 70 80", "Dział Ekologii"},
                {"Łukasz", "Piotrowski", "l.piotrowski@pafw.pl", "+48 22 900 80 90", "Dyrektor Programowy"},
                {"Dorota", "Grabowska", "d.grabowska@pafw.pl", "+48 22 900 80 91", "Specjalista ds. ewaluacji"},
                {"Zofia", "Pawlak", "kontakt@funduszngo.pl", "+48 500 900 100", "Koordynator Zbiórek"},
                {"Marcin", "Michalski", "rozliczenia@funduszngo.pl", "+48 500 900 101", "Dział Audytu"}
            };

            for (int i = 0; i < scData.length; i++) {
                SponsorContact sc = new SponsorContact();
                sc.setFirstName(scData[i][0]);
                sc.setLastName(scData[i][1]);
                sc.setEmail(scData[i][2]);
                sc.setPhone(scData[i][3]);
                sc.setPosition(scData[i][4]);
                sc.setSponsor(sponsors.get(i % sponsors.size()));
                contacts.add(sc);
            }
            sponsorContactRepository.saveAll(contacts);
        }

        // 7. 6 KATEGORII (BUDGET ITEM TEMPLATES)
        List<BudgetItemTemplate> templates;
        if (budgetItemTemplateRepository.count() == 0) {
            List<BudgetItemTemplate> toSave = new ArrayList<>();
            Object[][] tmplData = {
                {"KOSZT_PER", "Wynagrodzenia", "Wynagrodzenia personelu merytorycznego i zarządczego", BudgetItemTemplate.CategoryType.PERSONNEL},
                {"KOSZT_MAT", "Produkcja publikacji", "Planowane publikacje, broszury, raporty i materiały drukowane", BudgetItemTemplate.CategoryType.OTHER},
                {"KOSZT_KSW", "Konferencje, spotkania, warsztaty", "Planowane konferencje, spotkania partnerskie i warsztaty merytoryczne", BudgetItemTemplate.CategoryType.SERVICES},
                {"KOSZT_LOG", "Podróże", "Podróże służbowe krajowe i zagraniczne", BudgetItemTemplate.CategoryType.TRAVEL},
                {"KOSZT_PROM", "Promocja i Komunikacja", "Druk ulotek, reklama w mediach, strona www", BudgetItemTemplate.CategoryType.SERVICES},
                {"KOSZT_ADM", "Koszty Administracyjne", "Obsługa księgowa, biurowa, opłaty bankowe i prawne", BudgetItemTemplate.CategoryType.OFFICE},
                {"KOSZT_SPRZ", "Sprzęt i Wyposażenie", "Zakup laptopów, rzutników i sprzętu konferencyjnego", BudgetItemTemplate.CategoryType.EQUIPMENT}
            };
            int displayOrder = 10;
            for (Object[] t : tmplData) {
                BudgetItemTemplate bit = new BudgetItemTemplate();
                bit.setCode((String) t[0]);
                bit.setName((String) t[1]);
                bit.setDescription((String) t[2]);
                bit.setDefaultCode((String) t[0]);
                bit.setDefaultCategory((BudgetItemTemplate.CategoryType) t[3]);
                bit.setDisplayOrder(displayOrder);
                displayOrder += 10;
                toSave.add(bit);
            }
            templates = budgetItemTemplateRepository.saveAll(toSave);
        } else {
            templates = budgetItemTemplateRepository.findAll();
        }

        // 8. 5 PROJEKTÓW
        List<Project> projects;
        if (projectRepository.count() == 0) {
            List<Project> toSave = new ArrayList<>();
            String[][] projData = {
                {"Akademia Młodego Lidera 2026", "CYKL_2026_01", "Szkolenia z zakresu społeczeństwa obywatelskiego", "2026-01-01", "2026-12-31", "150000.00"},
                {"Zielona Gmina - Eko Edukacja", "EKO_2026_02", "Warsztaty ekologiczne dla szkół podstawowych", "2026-03-01", "2026-10-31", "80000.00"},
                {"Cyfrowy Senior w Świecie Online", "SENIOR_2026_03", "Kursy obsługi komputera i smartfona dla seniorów", "2026-02-01", "2026-11-30", "60000.00"},
                {"Kultura Bez Granic - Festiwal", "KULT_2026_04", "Integracyjny festiwal artystyczny i lokalny", "2026-05-01", "2026-09-30", "120000.00"},
                {"Wsparcie Psychologiczne NGO", "PSYCH_2026_05", "Bezpłatne poradnictwo dla osób w kryzysie", "2026-01-01", "2026-12-31", "95000.00"}
            };

            for (int i = 0; i < projData.length; i++) {
                String[] p = projData[i];
                Project proj = new Project();
                proj.setName(p[0]);
                proj.setCode(p[1]);
                proj.setDescription(p[2]);
                proj.setStartDate(LocalDate.parse(p[3]));
                proj.setEndDate(LocalDate.parse(p[4]));
                proj.setTotalBudget(new BigDecimal(p[5]));
                if (!employees.isEmpty()) {
                    proj.setGrantCoordinator(employees.get(i % employees.size()));
                }
                toSave.add(proj);
            }
            projects = projectRepository.saveAll(toSave);
        } else {
            projects = projectRepository.findAll();
        }

        // 9. GRANT + POZYCJE BUDŻETOWE (wymagane przez model Expenditure)
        List<Grant> grants;
        if (grantRepository.count() == 0 && !projects.isEmpty() && !sponsors.isEmpty() && !templates.isEmpty()) {
            List<Grant> toSave = new ArrayList<>();
            for (int i = 0; i < projects.size(); i++) {
                Project project = projects.get(i);
                Grant grant = new Grant();
                grant.setCode("G-" + project.getCode());
                grant.setName(project.getName());
                grant.setSponsor(sponsors.get(i % sponsors.size()));
                grant.setProject(project);
                grant.setStartDate(project.getStartDate());
                grant.setEndDate(project.getEndDate());
                grant.setTotalAmount(project.getTotalBudget());
                grant.setCurrency("PLN");
                grant.setGrantCoordinator(project.getGrantCoordinator());
                grant.setActive(true);

                for (BudgetItemTemplate template : templates) {
                    GrantBudgetItem item = new GrantBudgetItem();
                    item.setGrant(grant);
                    item.setName(template.getName());
                    item.setCode(template.getCode());
                    item.setAccountingCode(template.getDefaultCode());
                    item.setPlannedAmount(
                            project.getTotalBudget()
                                    .divide(BigDecimal.valueOf(templates.size()), 2, RoundingMode.HALF_UP)
                    );
                    item.setActive(true);
                    grant.getBudgetItems().add(item);
                }
                toSave.add(grant);
            }
            grants = grantRepository.saveAll(toSave);
        } else {
            grants = grantRepository.findAll();
        }

        seedPublicationsIfMissing(grants);
    }

    private void removeBudgetDataIfConfigured() {
        if (!allowDestructiveClear) {
            return;
        }
        if (clear2025OnStartup) {
            removeBudgetData(FISCAL_YEAR_2025);
        }
        if (clear2026OnStartup) {
            removeBudgetData(FISCAL_YEAR_2026);
        }
    }

    private void removeBudgetData(int fiscalYear) {
        LocalDate yearStart = LocalDate.of(fiscalYear, 1, 1);
        LocalDate yearEnd = LocalDate.of(fiscalYear, 12, 31);

        List<Project> projects = projectRepository.findAll().stream()
                .filter(project -> project.getStartDate() != null
                        && project.getStartDate().getYear() == fiscalYear)
                .toList();
        List<Long> projectIds = projects.stream().map(Project::getId).toList();

        List<Grant> grants = grantRepository.findAll().stream()
                .filter(grant -> grant.getProject() != null
                        && projectIds.contains(grant.getProject().getId()))
                .toList();
        List<Long> grantIds = grants.stream().map(Grant::getId).toList();

        long expenditures = expenditureRepository.countByFiscalYear(fiscalYear);
        long allocations = costAllocationRepository.countByFiscalYear(fiscalYear);

        costAllocationRepository.deleteExpenditureAllocationsByFiscalYear(fiscalYear);
        if (!grantIds.isEmpty()) {
            costAllocationRepository.deleteExpenditureAllocationsByGrantIdIn(grantIds);
            expenditureAllocationRepository.deleteByGrantIdIn(grantIds);
            grantCoverageRepository.deleteByGrantIdIn(grantIds);
            if (grantRepository.countLegacyPersonnelAllocationsTable() > 0) {
                grantRepository.deleteLegacyPersonnelAllocationsByGrantIdIn(grantIds);
            }
            expenditureRepository.deleteByGrantIdIn(grantIds);
            publicationRepository.deleteByGrantIdIn(grantIds);
            plannedEventRepository.deleteByGrantIdIn(grantIds);
            travelBudgetLineRepository.deleteByGrantIdIn(grantIds);
            grantRepository.deleteAll(grants);
        }
        expenditureRepository.deleteByFiscalYear(fiscalYear);
        costAllocationRepository.deletePlanAllocationsByFiscalYear(fiscalYear);
        plannedEventRepository.deleteByEventDateBetween(yearStart, yearEnd);

        if (!projectIds.isEmpty()) {
            costAllocationRepository.deleteByProjectIdIn(projectIds);
            projectRepository.deleteAll(projects);
        }

        if (expenditures == 0 && allocations == 0 && projects.isEmpty() && grants.isEmpty()) {
            return;
        }

        log.info(
                "Usunięto dane operacyjne {} (kategorie budżetowe zachowane): {} projektów, {} grantów, {} wydatków, {} alokacji",
                fiscalYear, projects.size(), grants.size(), expenditures, allocations);
    }

    private void ensureDemoProjectsAndGrants2025() {
        ensureDemoProjectsAndGrants(new String[][]{
                {"Akademia Młodego Lidera 2025", "CYKL_2025_01", "Szkolenia z zakresu społeczeństwa obywatelskiego", "2025-01-01", "2025-12-31", "140000.00"},
                {"Zielona Gmina - Eko Edukacja 2025", "EKO_2025_02", "Warsztaty ekologiczne dla szkół podstawowych", "2025-03-01", "2025-10-31", "75000.00"},
                {"Cyfrowy Senior w Świecie Online 2025", "SENIOR_2025_03", "Kursy obsługi komputera i smartfona dla seniorów", "2025-02-01", "2025-11-30", "55000.00"},
                {"Kultura Bez Granic - Festiwal 2025", "KULT_2025_04", "Integracyjny festiwal artystyczny i lokalny", "2025-05-01", "2025-09-30", "110000.00"},
                {"Wsparcie Psychologiczne NGO 2025", "PSYCH_2025_05", "Bezpłatne poradnictwo dla osób w kryzysie", "2025-01-01", "2025-12-31", "90000.00"}
        }, "2025");
    }

    private void ensureDemoProjectsAndGrants2026() {
        ensureDemoProjectsAndGrants(new String[][]{
                {"Akademia Młodego Lidera 2026", "CYKL_2026_01", "Szkolenia z zakresu społeczeństwa obywatelskiego", "2026-01-01", "2026-12-31", "150000.00"},
                {"Zielona Gmina - Eko Edukacja", "EKO_2026_02", "Warsztaty ekologiczne dla szkół podstawowych", "2026-03-01", "2026-10-31", "80000.00"},
                {"Cyfrowy Senior w Świecie Online", "SENIOR_2026_03", "Kursy obsługi komputera i smartfona dla seniorów", "2026-02-01", "2026-11-30", "60000.00"},
                {"Kultura Bez Granic - Festiwal", "KULT_2026_04", "Integracyjny festiwal artystyczny i lokalny", "2026-05-01", "2026-09-30", "120000.00"},
                {"Wsparcie Psychologiczne NGO", "PSYCH_2026_05", "Bezpłatne poradnictwo dla osób w kryzysie", "2026-01-01", "2026-12-31", "95000.00"}
        }, "2026");
    }

    private void ensureDemoProjectsAndGrants(String[][] projData, String yearLabel) {
        List<Sponsor> sponsors = sponsorRepository.findAll();
        List<BudgetItemTemplate> templates = budgetItemTemplateRepository.findAll();
        List<Employee> employees = employeeRepository.findAll();
        if (sponsors.isEmpty() || templates.isEmpty()) {
            return;
        }

        int grantsCreated = 0;
        for (int i = 0; i < projData.length; i++) {
            String[] row = projData[i];
            Project project = projectRepository.findByCode(row[1]).orElse(null);
            if (project == null) {
                project = new Project();
                project.setName(row[0]);
                project.setCode(row[1]);
                project.setDescription(row[2]);
                project.setStartDate(LocalDate.parse(row[3]));
                project.setEndDate(LocalDate.parse(row[4]));
                project.setTotalBudget(new BigDecimal(row[5]));
                project.setActive(true);
                if (!employees.isEmpty()) {
                    project.setGrantCoordinator(employees.get(i % employees.size()));
                }
                project = projectRepository.save(project);
            }

            String grantCode = "G-" + project.getCode();
            if (grantRepository.findByCode(grantCode).isEmpty()) {
                Grant grant = new Grant();
                grant.setCode(grantCode);
                grant.setName(project.getName());
                grant.setSponsor(sponsors.get(i % sponsors.size()));
                grant.setProject(project);
                grant.setStartDate(project.getStartDate());
                grant.setEndDate(project.getEndDate());
                grant.setTotalAmount(project.getTotalBudget());
                grant.setCurrency("PLN");
                grant.setGrantCoordinator(project.getGrantCoordinator());
                grant.setActive(true);

                BigDecimal divisor = BigDecimal.valueOf(Math.max(templates.size(), 1));
                for (BudgetItemTemplate template : templates) {
                    GrantBudgetItem item = new GrantBudgetItem();
                    item.setGrant(grant);
                    item.setName(template.getName());
                    item.setCode(template.getCode());
                    item.setAccountingCode(template.getDefaultCode() != null ? template.getDefaultCode() : template.getCode());
                    item.setPlannedAmount(project.getTotalBudget().divide(divisor, 2, RoundingMode.HALF_UP));
                    item.setActive(true);
                    grant.getBudgetItems().add(item);
                }
                grantRepository.save(grant);
                grantsCreated++;
            }
        }
        if (grantsCreated > 0) {
            log.info("Uzupełniono {} grantów demo dla roku {}", grantsCreated, yearLabel);
        }
    }

    private void backfillExpenditureFiscalYears() {
        expenditureRepository.findAll().stream()
                .filter(exp -> exp.getFiscalYear() == null && exp.getIssueDate() != null)
                .forEach(exp -> {
                    exp.setFiscalYear(exp.getIssueDate().getYear());
                    expenditureRepository.save(exp);
                });
    }

    private void syncGrantDisplayNames() {
        grantRepository.findAll().forEach(grant -> {
            String name = grant.getName();
            if (name != null && name.startsWith("Grant: ")) {
                grant.setName(name.substring("Grant: ".length()));
                grantRepository.save(grant);
            }
        });
    }

    private Role ensureRole(String name, String description) {
        return roleRepository.findByName(name).orElseGet(() -> {
            Role r = new Role();
            r.setName(name);
            r.setDescription(description);
            return roleRepository.save(r);
        });
    }

    private void runEmptyInstall(Role adminRole, Role editRole) {
        Organization organization = ensureEmptyOrganization();
        syncAdminUser(adminRole, organization);
        syncEditorUser(editRole, organization);
        ensureAllStandardBudgetTemplates();
        budgetSetupService.ensureBudgetCategoryDisplayOrder();
        log.info(
                "Pusta instalacja: organizacja={} ({}), szablony={}, pracownicy={}, granty={}, wydatki={}",
                organization.getName(),
                organization.getHost(),
                budgetItemTemplateRepository.count(),
                employeeRepository.count(),
                grantRepository.count(),
                expenditureRepository.count()
        );
    }

    private Organization ensureEmptyOrganization() {
        String host = demoOrgHost != null && !demoOrgHost.isBlank() ? demoOrgHost.trim() : emptyOrgCode;
        Organization organization = organizationRepository.findByCode(emptyOrgCode)
                .or(() -> organizationRepository.findByHost(host))
                .orElseGet(Organization::new);
        if (organization.getId() != null) {
            return organization;
        }
        organization.setCode(emptyOrgCode);
        organization.setName(emptyOrgName);
        organization.setLegalName(emptyOrgLegalName);
        organization.setHost(host);
        organization.setEmail(adminEmail);
        organization.setDemo(false);
        organization.setActive(true);
        Organization saved = organizationRepository.save(organization);
        log.info("Utworzono pustą organizację: {} ({})", saved.getName(), saved.getHost());
        return saved;
    }

    private Organization ensureDemoOrganization() {
        Organization organization = organizationRepository.findByCode(DEMO_ORG_CODE).orElseGet(Organization::new);
        organization.setCode(DEMO_ORG_CODE);
        organization.setName("Green Future");
        organization.setLegalName("Fundacja Green Future — Centrum Edukacji Ekologicznej i Rozwoju Społecznego");
        organization.setNip("7010123456");
        organization.setRegon("385947261");
        organization.setStreet("ul. Ekologiczna 12");
        organization.setPostalCode("00-640");
        organization.setCity("Warszawa");
        organization.setEmail("biuro@greenfuture.org.pl");
        organization.setPhone("+48 22 123 45 67");
        organization.setWebsite("https://greenfuture.org.pl");
        organization.setHost(demoOrgHost != null && !demoOrgHost.isBlank() ? demoOrgHost : DEMO_ORG_HOST);
        organization.setDemo(true);
        organization.setActive(true);
        Organization saved = organizationRepository.save(organization);
        log.info("Organizacja demo: {} ({})", saved.getName(), saved.getHost());
        return saved;
    }

    private void syncAdminUser(Role adminRole, Organization organization) {
        User admin = userRepository.findByEmail(adminEmail).orElseGet(User::new);
        admin.setEmail(adminEmail);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setFirstName(adminFirstName);
        admin.setLastName(adminLastName);
        admin.setEnabled(true);
        admin.setOrganization(organization);
        admin.setRoles(new HashSet<>(Set.of(adminRole)));
        userRepository.save(admin);
    }

    private void syncEditorUser(Role editRole, Organization organization) {
        if (editorEmail == null || editorEmail.isBlank()) {
            return;
        }
        String email = editorEmail.trim().toLowerCase();
        User editor = userRepository.findByEmail(email).orElse(null);
        if (editor == null) {
            if (editorPassword == null || editorPassword.isBlank()) {
                log.info("Konto edycji {} nie istnieje. Dodaj je w panelu użytkowników.", email);
                return;
            }
            editor = new User();
            editor.setEmail(email);
            editor.setPassword(passwordEncoder.encode(editorPassword));
            editor.setFirstName(editorFirstName);
            editor.setLastName(editorLastName);
            editor.setEnabled(true);
            editor.setOrganization(organization);
            editor.setRoles(new HashSet<>(Set.of(editRole)));
            userRepository.save(editor);
            log.info("Utworzono konto edycji: {}", email);
            return;
        }
        if (editor.getOrganization() == null) {
            editor.setOrganization(organization);
        }
        if (needsEditorRole(editor)) {
            editor.setRoles(new HashSet<>(Set.of(editRole)));
            log.info("Konto {} dostało rolę edycji.", email);
        }
        if (editorPassword != null && !editorPassword.isBlank()) {
            editor.setPassword(passwordEncoder.encode(editorPassword));
        }
        userRepository.save(editor);
    }

    private static boolean needsEditorRole(User editor) {
        if (editor.getRoles() == null || editor.getRoles().isEmpty()) {
            return true;
        }
        return editor.getRoles().stream().allMatch(role -> AppRoles.isLegacy(role.getName()));
    }

    private void seedPublicationsIfMissing() {
        seedPublicationsIfMissing(grantRepository.findAll());
    }

    private void seedPublicationsIfMissing(List<Grant> grants) {
        if (grants.isEmpty()) {
            return;
        }
        if (publicationRepository.count() >= 6) {
            return;
        }
        if (publicationRepository.count() > 0) {
            publicationRepository.deleteAll();
            log.warn("Naprawa seed publikacji — ponowne załadowanie danych");
        }

        Object[][] pubData = {
            {"Poradnik lidera lokalnego", "8500.00", 0},
            {"Broszura ekologiczna dla szkół", "4200.00", 1},
            {"Skrypt warsztatów dla seniorów", "3100.00", 2},
            {"Katalog festiwalu Kultura Bez Granic", "12000.00", 3},
            {"Ulotka programu wsparcia psychologicznego", "2800.00", 4},
            {"Raport roczny NGO 2026", "6500.00", 0}
        };

        List<Publication> publications = new ArrayList<>();
        for (Object[] row : pubData) {
            Publication publication = new Publication();
            publication.setTitle((String) row[0]);
            publication.setPlannedCost(new BigDecimal((String) row[1]));
            int grantIndex = (Integer) row[2];
            if (grantIndex < grants.size()) {
                publication.setGrant(grants.get(grantIndex));
            }
            publication.setActive(true);
            publications.add(publication);
        }
        publicationRepository.saveAll(publications);
    }

    private void ensureEventsCategoryAndSeed() {
        BudgetItemTemplate template = budgetItemTemplateRepository.findByCode(EVENTS_TEMPLATE_CODE)
                .orElseGet(() -> {
                    BudgetItemTemplate bit = new BudgetItemTemplate();
                    bit.setCode(EVENTS_TEMPLATE_CODE);
                    bit.setName(EVENTS_TEMPLATE_NAME);
                    bit.setDescription("Planowane konferencje, spotkania partnerskie i warsztaty merytoryczne");
                    bit.setDefaultCode(EVENTS_TEMPLATE_CODE);
                    bit.setDefaultCategory(BudgetItemTemplate.CategoryType.SERVICES);
                    return budgetItemTemplateRepository.save(bit);
                });

        List<Grant> grants = grantRepository.findAllWithBudgetItems();
        for (Grant grant : grants) {
            boolean exists = grant.getBudgetItems().stream()
                    .anyMatch(item -> EVENTS_TEMPLATE_CODE.equals(item.getCode())
                            || EVENTS_TEMPLATE_NAME.equals(item.getName()));
            if (!exists) {
                GrantBudgetItem item = new GrantBudgetItem();
                item.setGrant(grant);
                item.setName(template.getName());
                item.setCode(template.getCode());
                item.setAccountingCode(template.getDefaultCode());
                item.setPlannedAmount(
                        grant.getTotalAmount() != null
                                ? grant.getTotalAmount().divide(BigDecimal.valueOf(7), 2, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO
                );
                item.setActive(true);
                grant.getBudgetItems().add(item);
                grantRepository.save(grant);
            }
        }

        seedPlannedEventsIfMissing(grants);
    }

    private void seedPlannedEventsIfMissing(List<Grant> grants) {
        if (plannedEventRepository.count() > 0 || grants.isEmpty()) {
            return;
        }

        Object[][] eventData = {
            {"Konferencja otwierająca Akademii Liderów", "2026-02-15", "15000.00", 0},
            {"Warsztaty ekologiczne dla nauczycieli", "2026-04-10", "8500.00", 1},
            {"Spotkanie networkingowe seniorów", "2026-03-22", "3200.00", 2},
            {"Konferencja podsumowująca festiwal", "2026-09-05", "18000.00", 3},
            {"Warsztaty psychologiczne dla NGO", "2026-06-18", "5600.00", 4},
            {"Spotkanie koordynatorów projektów", "2026-05-08", "4500.00", 0},
            {"Konferencja regionalna partnerów", "2026-10-12", "11000.00", 1}
        };

        List<PlannedEvent> events = new ArrayList<>();
        for (Object[] row : eventData) {
            PlannedEvent event = new PlannedEvent();
            event.setTitle((String) row[0]);
            event.setEventDate(LocalDate.parse((String) row[1]));
            event.setPlannedCost(new BigDecimal((String) row[2]));
            int grantIndex = (Integer) row[3];
            if (grantIndex < grants.size()) {
                event.setGrant(grants.get(grantIndex));
            }
            event.setActive(true);
            events.add(event);
        }
        plannedEventRepository.saveAll(events);
    }

    private void ensureTravelCategoryAndSeed() {
        BudgetItemTemplate template = budgetItemTemplateRepository.findByCode(TRAVEL_TEMPLATE_CODE)
                .orElseGet(() -> {
                    BudgetItemTemplate bit = new BudgetItemTemplate();
                    bit.setCode(TRAVEL_TEMPLATE_CODE);
                    bit.setName(TRAVEL_TEMPLATE_NAME);
                    bit.setDescription("Podróże służbowe krajowe i zagraniczne");
                    bit.setDefaultCode(TRAVEL_TEMPLATE_CODE);
                    bit.setDefaultCategory(BudgetItemTemplate.CategoryType.TRAVEL);
                    return budgetItemTemplateRepository.save(bit);
                });

        if ("Transport i Logistyka".equals(template.getName())) {
            template.setName(TRAVEL_TEMPLATE_NAME);
            template.setDescription("Podróże służbowe krajowe i zagraniczne");
            budgetItemTemplateRepository.save(template);
        }

        List<Grant> grants = grantRepository.findAllWithBudgetItems();
        for (Grant grant : grants) {
            grant.getBudgetItems().stream()
                    .filter(item -> "Transport i Logistyka".equals(item.getName()))
                    .forEach(item -> item.setName(TRAVEL_TEMPLATE_NAME));

            boolean exists = grant.getBudgetItems().stream()
                    .anyMatch(item -> TRAVEL_TEMPLATE_CODE.equals(item.getCode())
                            || TRAVEL_TEMPLATE_NAME.equals(item.getName()));
            if (!exists) {
                GrantBudgetItem item = new GrantBudgetItem();
                item.setGrant(grant);
                item.setName(template.getName());
                item.setCode(template.getCode());
                item.setAccountingCode(template.getDefaultCode());
                item.setPlannedAmount(
                        grant.getTotalAmount() != null
                                ? grant.getTotalAmount().divide(BigDecimal.valueOf(7), 2, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO
                );
                item.setActive(true);
                grant.getBudgetItems().add(item);
            }
            grantRepository.save(grant);
        }

        seedTravelLinesIfMissing(grants);
    }

    private void seedTravelLinesIfMissing(List<Grant> grants) {
        if (travelBudgetLineRepository.count() > 0 && !overwriteExisting) {
            return;
        }
        if (travelBudgetLineRepository.count() > 0) {
            travelBudgetLineRepository.deleteAll();
        }
        if (grants.isEmpty()) {
            return;
        }

        Object[][] lineData = {
            {TravelBudgetLine.TravelScope.DOMESTIC, TravelBudgetLine.ExpenseType.TRANSPORT, "1500.00", 0},
            {TravelBudgetLine.TravelScope.DOMESTIC, TravelBudgetLine.ExpenseType.TRANSPORT, "1000.00", 1},
            {TravelBudgetLine.TravelScope.DOMESTIC, TravelBudgetLine.ExpenseType.TRANSPORT, "1000.00", 2},
            {TravelBudgetLine.TravelScope.DOMESTIC, TravelBudgetLine.ExpenseType.HOTEL, "1400.00", 0},
            {TravelBudgetLine.TravelScope.DOMESTIC, TravelBudgetLine.ExpenseType.HOTEL, "1400.00", 1},
            {TravelBudgetLine.TravelScope.DOMESTIC, TravelBudgetLine.ExpenseType.HOTEL, "1400.00", 2},
            {TravelBudgetLine.TravelScope.DOMESTIC, TravelBudgetLine.ExpenseType.MEALS, "600.00", 0},
            {TravelBudgetLine.TravelScope.DOMESTIC, TravelBudgetLine.ExpenseType.MEALS, "600.00", 1},
            {TravelBudgetLine.TravelScope.DOMESTIC, TravelBudgetLine.ExpenseType.MEALS, "600.00", 2},
            {TravelBudgetLine.TravelScope.INTERNATIONAL, TravelBudgetLine.ExpenseType.TRANSPORT, "4000.00", 1},
            {TravelBudgetLine.TravelScope.INTERNATIONAL, TravelBudgetLine.ExpenseType.TRANSPORT, "4000.00", 2},
            {TravelBudgetLine.TravelScope.INTERNATIONAL, TravelBudgetLine.ExpenseType.TRANSPORT, "4000.00", 3},
            {TravelBudgetLine.TravelScope.INTERNATIONAL, TravelBudgetLine.ExpenseType.HOTEL, "2833.33", 1},
            {TravelBudgetLine.TravelScope.INTERNATIONAL, TravelBudgetLine.ExpenseType.HOTEL, "2833.33", 2},
            {TravelBudgetLine.TravelScope.INTERNATIONAL, TravelBudgetLine.ExpenseType.HOTEL, "2833.34", 4},
            {TravelBudgetLine.TravelScope.INTERNATIONAL, TravelBudgetLine.ExpenseType.MEALS, "1066.67", 1},
            {TravelBudgetLine.TravelScope.INTERNATIONAL, TravelBudgetLine.ExpenseType.MEALS, "1066.67", 2},
            {TravelBudgetLine.TravelScope.INTERNATIONAL, TravelBudgetLine.ExpenseType.MEALS, "1066.66", 3}
        };

        List<TravelBudgetLine> lines = new ArrayList<>();
        for (Object[] row : lineData) {
            TravelBudgetLine line = new TravelBudgetLine();
            line.setScope((TravelBudgetLine.TravelScope) row[0]);
            line.setExpenseType((TravelBudgetLine.ExpenseType) row[1]);
            line.setPlannedCost(new BigDecimal((String) row[2]));
            int grantIndex = (Integer) row[3];
            if (grantIndex < grants.size()) {
                line.setGrant(grants.get(grantIndex));
            }
            line.setActive(true);
            lines.add(line);
        }
        travelBudgetLineRepository.saveAll(lines);
    }

    private void syncEmployeesFromSeed() {
        for (String[] row : EMPLOYEE_SEED) {
            var existing = employeeRepository.findByEmail(row[3]);
            if (existing.isPresent() && !overwriteExisting) {
                continue;
            }

            Employee employee = existing.orElseGet(Employee::new);
            employee.setFirstName(row[0]);
            employee.setLastName(row[1]);
            employee.setPosition(row[2]);
            employee.setEmail(row[3]);
            employee.setPhone(row[4]);
            employee.setPlannedCost(new BigDecimal(row[5]));
            employeeRepository.save(employee);
        }
    }

    private void ensureEmployeesFromSeedExist() {
        for (String[] row : EMPLOYEE_SEED) {
            if (employeeRepository.findByEmail(row[3]).isPresent()) {
                continue;
            }
            Employee employee = new Employee();
            employee.setFirstName(row[0]);
            employee.setLastName(row[1]);
            employee.setPosition(row[2]);
            employee.setEmail(row[3]);
            employee.setPhone(row[4]);
            employee.setPlannedCost(new BigDecimal(row[5]));
            employeeRepository.save(employee);
            log.info("Utworzono brakującego pracownika seed: {}", row[3]);
        }
    }

    private void ensureAllStandardBudgetTemplates() {
        ensureBudgetTemplate(
                PERSONNEL_TEMPLATE_CODE,
                "Wynagrodzenia",
                "Wynagrodzenia personelu merytorycznego i zarządczego",
                BudgetItemTemplate.CategoryType.PERSONNEL
        );
        ensureBudgetTemplate(
                PUBLICATIONS_TEMPLATE_CODE,
                "Produkcja publikacji",
                "Planowane publikacje, broszury, raporty i materiały drukowane",
                BudgetItemTemplate.CategoryType.OTHER
        );
        ensureBudgetTemplate(
                EVENTS_TEMPLATE_CODE,
                EVENTS_TEMPLATE_NAME,
                "Planowane konferencje, spotkania partnerskie i warsztaty merytoryczne",
                BudgetItemTemplate.CategoryType.SERVICES
        );
        ensureBudgetTemplate(
                TRAVEL_TEMPLATE_CODE,
                TRAVEL_TEMPLATE_NAME,
                "Podróże służbowe krajowe i zagraniczne",
                BudgetItemTemplate.CategoryType.TRAVEL
        );
        ensureBudgetTemplate(
                PROMOTION_TEMPLATE_CODE,
                "Promocja i Komunikacja",
                "Druk ulotek, reklama w mediach, strona www",
                BudgetItemTemplate.CategoryType.SERVICES
        );
        ensureBudgetTemplate(
                ADMIN_TEMPLATE_CODE,
                "Koszty Administracyjne",
                "Obsługa księgowa, biurowa, opłaty bankowe i prawne",
                BudgetItemTemplate.CategoryType.OFFICE
        );
        ensureBudgetTemplate(
                EQUIPMENT_TEMPLATE_CODE,
                "Sprzęt i Wyposażenie",
                "Zakup laptopów, rzutników i sprzętu konferencyjnego",
                BudgetItemTemplate.CategoryType.EQUIPMENT
        );
    }

    private BudgetItemTemplate ensureBudgetTemplate(String code,
                                                    String name,
                                                    String description,
                                                    BudgetItemTemplate.CategoryType categoryType) {
        return budgetItemTemplateRepository.findByCode(code)
                .orElseGet(() -> budgetItemTemplateRepository.findByName(name)
                        .map(existing -> {
                            existing.setCode(code);
                            if (existing.getDefaultCode() == null || existing.getDefaultCode().isBlank()) {
                                existing.setDefaultCode(code);
                            }
                            return budgetItemTemplateRepository.save(existing);
                        })
                        .orElseGet(() -> {
                            BudgetItemTemplate template = new BudgetItemTemplate();
                            template.setCode(code);
                            template.setName(name);
                            template.setDescription(description);
                            template.setDefaultCode(code);
                            template.setDefaultCategory(categoryType);
                            return budgetItemTemplateRepository.save(template);
                        }));
    }

    private void ensureAllGrantBudgetItems() {
        List<BudgetItemTemplate> templates = budgetItemTemplateRepository.findAll();
        if (templates.isEmpty()) {
            return;
        }

        List<Grant> grants = grantRepository.findAllWithBudgetItems();
        int templateCount = templates.size();
        BigDecimal divisor = BigDecimal.valueOf(Math.max(templateCount, 1));

        for (Grant grant : grants) {
            boolean changed = false;
            for (BudgetItemTemplate template : templates) {
                boolean exists = grant.getBudgetItems().stream()
                        .anyMatch(item -> template.getCode().equals(item.getCode())
                                || template.getName().equals(item.getName()));
                if (exists) {
                    continue;
                }

                GrantBudgetItem item = new GrantBudgetItem();
                item.setGrant(grant);
                item.setName(template.getName());
                item.setCode(template.getCode());
                item.setAccountingCode(template.getDefaultCode() != null
                        ? template.getDefaultCode()
                        : template.getCode());
                item.setPlannedAmount(
                        grant.getTotalAmount() != null
                                ? grant.getTotalAmount().divide(divisor, 2, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO
                );
                item.setActive(true);
                grant.getBudgetItems().add(item);
                changed = true;
            }
            if (changed) {
                grantRepository.save(grant);
                log.info("Uzupełniono pozycje budżetowe grantu: {}", grant.getName());
            }
        }
    }
}
