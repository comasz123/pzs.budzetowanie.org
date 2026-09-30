package pl.ngo.budget.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.ngo.budget.dto.GrantBudgetSaveCommand;
import pl.ngo.budget.dto.GrantSaveCommand;
import pl.ngo.budget.entity.cost.Employee;
import pl.ngo.budget.entity.coverage.BudgetItemTemplate;
import pl.ngo.budget.entity.coverage.Grant;
import pl.ngo.budget.entity.coverage.GrantBudgetItem;
import pl.ngo.budget.entity.coverage.GrantTranche;
import pl.ngo.budget.entity.coverage.Project;
import pl.ngo.budget.entity.coverage.Sponsor;
import pl.ngo.budget.entity.coverage.SponsorContact;
import pl.ngo.budget.repository.*;
import pl.ngo.budget.dto.GrantBudgetViewDto;
import pl.ngo.budget.dto.OrgBudgetLineOptionDto;
import pl.ngo.budget.service.BudgetMatrixService;
import pl.ngo.budget.service.BudgetSetupService;
import pl.ngo.budget.service.GrantBudgetService;
import pl.ngo.budget.service.ViewExcelExportService;
import pl.ngo.budget.util.BudgetSection;
import pl.ngo.budget.util.ExcelDownload;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping({"/admin", "/realizacja"})
public class AdminController {

    private final GrantRepository grantRepository;
    private final SponsorRepository sponsorRepository;
    private final SponsorContactRepository sponsorContactRepository;
    private final EmployeeRepository employeeRepository;
    private final ProjectRepository projectRepository;
    private final BudgetSetupService budgetSetupService;
    private final GrantBudgetService grantBudgetService;
    private final BudgetMatrixService budgetMatrixService;
    private final ViewExcelExportService viewExcelExportService;

    public AdminController(GrantRepository grantRepository,
                           SponsorRepository sponsorRepository,
                           SponsorContactRepository sponsorContactRepository,
                           EmployeeRepository employeeRepository,
                           ProjectRepository projectRepository,
                           BudgetSetupService budgetSetupService,
                           GrantBudgetService grantBudgetService,
                           BudgetMatrixService budgetMatrixService,
                           ViewExcelExportService viewExcelExportService) {
        this.grantRepository = grantRepository;
        this.sponsorRepository = sponsorRepository;
        this.sponsorContactRepository = sponsorContactRepository;
        this.employeeRepository = employeeRepository;
        this.projectRepository = projectRepository;
        this.budgetSetupService = budgetSetupService;
        this.grantBudgetService = grantBudgetService;
        this.budgetMatrixService = budgetMatrixService;
        this.viewExcelExportService = viewExcelExportService;
    }

    // --- Granty ---

    @GetMapping("/grants")
    public String grants(@RequestParam(value = "year", required = false) Integer year, Model model) {
        int fiscalYear = year != null ? year : LocalDate.now().getYear();
        model.addAttribute("activeSection", "grants");
        model.addAttribute("fiscalYear", fiscalYear);
        model.addAttribute("grantRows", grantBudgetService.buildGrantListRows(fiscalYear));
        return "admin/grants";
    }

    @GetMapping("/grants/export")
    public void exportGrants(@RequestParam(value = "year", required = false) Integer year,
                             HttpServletResponse response) throws IOException {
        int fiscalYear = year != null ? year : LocalDate.now().getYear();
        ExcelDownload.prepare(response, "granty-" + fiscalYear + ".xlsx");
        viewExcelExportService.writeGrants(response.getOutputStream(), fiscalYear);
    }

    @GetMapping("/grants/{id}/budget")
    public String grantBudget(@PathVariable Long id,
                              @RequestParam(required = false) Boolean edit,
                              Model model) {
        model.addAttribute("activeSection", "grants");
        model.addAttribute("editMode", Boolean.TRUE.equals(edit));
        GrantBudgetViewDto grantBudget = grantBudgetService.buildGrantBudgetView(id);
        int fiscalYear = grantBudget.getStartDate() != null
                ? grantBudget.getStartDate().getYear()
                : LocalDate.now().getYear();
        List<OrgBudgetLineOptionDto> options = new java.util.ArrayList<>(
                budgetMatrixService.orgCoverageOptions(fiscalYear));
        Set<String> parents = budgetMatrixService.coverageParentSourceRefs(fiscalYear);
        Set<String> seen = new LinkedHashSet<>();
        options.forEach(option -> seen.add(option.getSourceRef()));
        for (GrantBudgetViewDto.GrantBudgetLineDto line : grantBudget.getLines()) {
            for (GrantBudgetViewDto.CoveredOrgBudgetLineDto coverage : line.getCoveredOrgLines()) {
                if (coverage.getSourceRef() == null || coverage.getSourceRef().isBlank()
                        || parents.contains(coverage.getSourceRef())
                        || !seen.add(coverage.getSourceRef())) {
                    continue;
                }
                options.add(OrgBudgetLineOptionDto.ofKey(
                        coverage.getOrgSourceType() != null ? coverage.getOrgSourceType() : "DASHBOARD_ROW",
                        coverage.getSourceRef().contains("|")
                                ? coverage.getSourceRef().substring(coverage.getSourceRef().indexOf('|') + 1)
                                : coverage.getSourceRef(),
                        line.getCode(),
                        coverage.getLabel() != null ? coverage.getLabel() : coverage.getSourceRef()));
            }
        }
        grantBudget.getOrgBudgetLineOptions().clear();
        grantBudget.getOrgBudgetLineOptions().addAll(options);
        model.addAttribute("grantBudget", grantBudget);
        return "admin/grant-budget";
    }

    @GetMapping("/grants/{id}/budget/export")
    public void exportGrantBudget(@PathVariable Long id, HttpServletResponse response) throws IOException {
        ExcelDownload.prepare(response, "grant-" + id + ".xlsx");
        viewExcelExportService.writeGrantBudget(response.getOutputStream(), id);
    }

    @PostMapping("/grants/{id}/budget")
    public String saveGrantBudget(@PathVariable Long id,
                                  @ModelAttribute GrantBudgetSaveCommand command,
                                  RedirectAttributes redirectAttributes) {
        try {
            grantBudgetService.saveGrantBudget(id, command);
            redirectAttributes.addFlashAttribute("successMessage", "Zapisano budżet grantu.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return sectionRedirect("/admin/grants/" + id + "/budget");
    }

    @GetMapping("/grants/new")
    public String newGrant(Model model) {
        populateGrantForm(model, new Grant(), "Nowy grant");
        return "admin/grant-form";
    }

    @GetMapping("/grants/{id}/edit")
    public String editGrant(@PathVariable Long id, Model model) {
        Grant grant = grantRepository.findByIdWithBudgetItems(id)
                .orElseThrow(() -> new IllegalArgumentException("Grant nie istnieje"));
        populateGrantForm(model, grant, "Edycja grantu");
        return "admin/grant-form";
    }

    @PostMapping("/grants")
    public String saveGrant(@ModelAttribute GrantSaveCommand command, RedirectAttributes redirectAttributes) {
        try {
            Grant grant = command.getId() != null
                    ? grantRepository.findByIdWithBudgetItems(command.getId())
                            .orElseThrow(() -> new IllegalArgumentException("Grant nie istnieje"))
                    : new Grant();
            grant.setCode(command.getCode().trim());
            grant.setName(command.getName().trim());
            grant.setSponsor(sponsorRepository.findById(command.getSponsorId())
                    .orElseThrow(() -> new IllegalArgumentException("Sponsor nie istnieje")));
            grant.setProject(projectRepository.findById(command.getProjectId())
                    .orElseThrow(() -> new IllegalArgumentException("Projekt nie istnieje")));
            grant.setStartDate(command.getStartDate());
            grant.setEndDate(command.getEndDate());
            grant.setTotalAmount(command.getTotalAmount());
            grant.setCurrency(command.getCurrency() != null && !command.getCurrency().isBlank()
                    ? command.getCurrency().trim()
                    : "PLN");
            grant.setPrimarySponsorContact(resolveContact(command.getPrimarySponsorContactId()));
            grant.setFinancialSponsorContact(resolveContact(command.getFinancialSponsorContactId()));
            grant.setGrantCoordinator(resolveEmployee(command.getGrantCoordinatorId()));
            grant.setActive(command.isActive());
            budgetSetupService.replaceGrantBudgetItems(grant, command.getBudgetItems());
            budgetSetupService.replaceGrantTranches(grant, command.getTranches());
            grantRepository.save(grant);
            redirectAttributes.addFlashAttribute("successMessage", "Zapisano grant: " + grant.getName());
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return command.getId() != null
                    ? sectionRedirect("/admin/grants/" + command.getId() + "/edit")
                    : sectionRedirect("/admin/grants/new");
        }
        return sectionRedirect("/admin/grants");
    }

    @PostMapping("/grants/{id}/delete")
    public String deleteGrant(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            grantRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("successMessage", "Usunięto grant.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Nie można usunąć grantu — jest powiązany z innymi danymi.");
        }
        return sectionRedirect("/admin/grants");
    }

    // --- Sponsorzy + kontakty ---

    @GetMapping("/sponsors")
    public String sponsors(Model model) {
        model.addAttribute("activeSection", "sponsors");
        model.addAttribute("sponsors", sponsorRepository.findAll().stream()
                .sorted(Comparator.comparing(Sponsor::getName, String.CASE_INSENSITIVE_ORDER))
                .toList());
        model.addAttribute("contacts", sponsorContactRepository.findAllWithSponsor());
        return "admin/sponsors";
    }

    @GetMapping("/sponsors/export")
    public void exportSponsors(HttpServletResponse response) throws IOException {
        ExcelDownload.prepare(response, "sponsorzy.xlsx");
        viewExcelExportService.writeSponsors(response.getOutputStream());
    }

    @GetMapping("/sponsors/new")
    public String newSponsor(Model model) {
        model.addAttribute("activeSection", "sponsors");
        model.addAttribute("sponsor", new Sponsor());
        model.addAttribute("pageTitle", "Nowy sponsor");
        return "admin/sponsor-form";
    }

    @GetMapping("/sponsors/{id}/edit")
    public String editSponsor(@PathVariable Long id, Model model) {
        Sponsor sponsor = sponsorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sponsor nie istnieje"));
        model.addAttribute("activeSection", "sponsors");
        model.addAttribute("sponsor", sponsor);
        model.addAttribute("pageTitle", "Edycja sponsora");
        return "admin/sponsor-form";
    }

    @PostMapping("/sponsors")
    public String saveSponsor(@RequestParam(required = false) Long id,
                                @RequestParam String name,
                                @RequestParam(required = false) String type,
                                @RequestParam(required = false) String notes,
                                @RequestParam(required = false) String street,
                                @RequestParam(required = false) String postalCode,
                                @RequestParam(required = false) String city,
                                @RequestParam(required = false) String country,
                                @RequestParam(required = false) String email,
                                @RequestParam(required = false) String phone,
                                RedirectAttributes redirectAttributes) {
        try {
            Sponsor sponsor = id != null
                    ? sponsorRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Sponsor nie istnieje"))
                    : new Sponsor();
            sponsor.setName(name.trim());
            sponsor.setType(blankToNull(type));
            sponsor.setNotes(blankToNull(notes));
            sponsor.setStreet(blankToNull(street));
            sponsor.setPostalCode(blankToNull(postalCode));
            sponsor.setCity(blankToNull(city));
            sponsor.setCountry(blankToNull(country));
            sponsor.setEmail(blankToNull(email));
            sponsor.setPhone(blankToNull(phone));
            sponsorRepository.save(sponsor);
            redirectAttributes.addFlashAttribute("successMessage", "Zapisano sponsora: " + sponsor.getName());
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return id != null ? sectionRedirect("/admin/sponsors/" + id + "/edit") : sectionRedirect("/admin/sponsors/new");
        }
        return sectionRedirect("/admin/sponsors");
    }

    @PostMapping("/sponsors/{id}/delete")
    public String deleteSponsor(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            sponsorRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("successMessage", "Usunięto sponsora.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Nie można usunąć sponsora — ma powiązane granty lub kontakty.");
        }
        return sectionRedirect("/admin/sponsors");
    }

    @GetMapping("/sponsor-contacts/new")
    public String newSponsorContact(@RequestParam(required = false) Long sponsorId, Model model) {
        populateContactForm(model, new SponsorContact(), "Nowy kontakt sponsora", sponsorId);
        return "admin/sponsor-contact-form";
    }

    @GetMapping("/sponsor-contacts/{id}/edit")
    public String editSponsorContact(@PathVariable Long id, Model model) {
        SponsorContact contact = sponsorContactRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Kontakt nie istnieje"));
        populateContactForm(model, contact, "Edycja kontaktu sponsora", contact.getSponsor().getId());
        return "admin/sponsor-contact-form";
    }

    @PostMapping("/sponsor-contacts")
    public String saveSponsorContact(@RequestParam(required = false) Long id,
                                     @RequestParam Long sponsorId,
                                     @RequestParam String firstName,
                                     @RequestParam String lastName,
                                     @RequestParam(required = false) String position,
                                     @RequestParam(required = false) String email,
                                     @RequestParam(required = false) String phone,
                                     @RequestParam(required = false) String notes,
                                     RedirectAttributes redirectAttributes) {
        try {
            SponsorContact contact = id != null
                    ? sponsorContactRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Kontakt nie istnieje"))
                    : new SponsorContact();
            contact.setSponsor(sponsorRepository.findById(sponsorId)
                    .orElseThrow(() -> new IllegalArgumentException("Sponsor nie istnieje")));
            contact.setFirstName(firstName.trim());
            contact.setLastName(lastName.trim());
            contact.setPosition(blankToNull(position));
            contact.setEmail(blankToNull(email));
            contact.setPhone(blankToNull(phone));
            contact.setNotes(blankToNull(notes));
            sponsorContactRepository.save(contact);
            redirectAttributes.addFlashAttribute("successMessage", "Zapisano kontakt: " + contact.getFirstName() + " " + contact.getLastName());
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return id != null ? sectionRedirect("/admin/sponsor-contacts/" + id + "/edit") : sectionRedirect("/admin/sponsor-contacts/new?sponsorId=" + sponsorId);
        }
        return sectionRedirect("/admin/sponsors");
    }

    @PostMapping("/sponsor-contacts/{id}/delete")
    public String deleteSponsorContact(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            sponsorContactRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("successMessage", "Usunięto kontakt sponsora.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Nie można usunąć kontaktu — jest powiązany z grantami.");
        }
        return sectionRedirect("/admin/sponsors");
    }

    // --- Pracownicy ---

    @GetMapping("/employees")
    public String employees(Model model) {
        model.addAttribute("activeSection", "employees");
        model.addAttribute("employees", employeeRepository.findAll().stream()
                .sorted(Comparator.comparing(Employee::getLastName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Employee::getFirstName, String.CASE_INSENSITIVE_ORDER))
                        .toList());
        return "admin/employees";
    }

    @GetMapping("/employees/export")
    public void exportEmployees(HttpServletResponse response) throws IOException {
        ExcelDownload.prepare(response, "pracownicy.xlsx");
        viewExcelExportService.writeEmployees(response.getOutputStream());
    }

    @GetMapping("/employees/new")
    public String newEmployee(Model model) {
        model.addAttribute("activeSection", "employees");
        model.addAttribute("employee", new Employee());
        model.addAttribute("pageTitle", "Nowy pracownik");
        return "admin/employee-form";
    }

    @GetMapping("/employees/{id}/edit")
    public String editEmployee(@PathVariable Long id, Model model) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pracownik nie istnieje"));
        model.addAttribute("activeSection", "employees");
        model.addAttribute("employee", employee);
        model.addAttribute("pageTitle", "Edycja pracownika");
        return "admin/employee-form";
    }

    @PostMapping("/employees")
    public String saveEmployee(@RequestParam(required = false) Long id,
                                 @RequestParam String firstName,
                                 @RequestParam String lastName,
                                 @RequestParam(required = false) String email,
                                 @RequestParam(required = false) String phone,
                                 @RequestParam(required = false) String position,
                                 @RequestParam(required = false) BigDecimal plannedCost,
                                 RedirectAttributes redirectAttributes) {
        try {
            Employee employee = id != null
                    ? employeeRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Pracownik nie istnieje"))
                    : new Employee();
            employee.setFirstName(firstName.trim());
            employee.setLastName(lastName.trim());
            employee.setEmail(blankToNull(email));
            employee.setPhone(blankToNull(phone));
            employee.setPosition(blankToNull(position));
            employee.setPlannedCost(plannedCost);
            employeeRepository.save(employee);
            budgetSetupService.syncEmployeeSalaryAllocations(employee.getId(), employee.getPlannedCost());
            redirectAttributes.addFlashAttribute("successMessage", "Zapisano pracownika: " + employee.getFirstName() + " " + employee.getLastName());
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return id != null ? sectionRedirect("/admin/employees/" + id + "/edit") : sectionRedirect("/admin/employees/new");
        }
        return sectionRedirect("/admin/employees");
    }

    @PostMapping("/employees/{id}/delete")
    public String deleteEmployee(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            employeeRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("successMessage", "Usunięto pracownika.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Nie można usunąć pracownika — ma powiązane alokacje kosztów.");
        }
        return sectionRedirect("/admin/employees");
    }

    // --- Projekty ---

    @GetMapping("/projects")
    public String projects(Model model) {
        populateProjectForm(model, new Project(), "Nowy projekt");
        return "admin/project-form";
    }

    @GetMapping("/projects/new")
    public String newProject() {
        return sectionRedirect("/admin/projects");
    }

    @PostMapping("/projects")
    public String saveProject(@RequestParam String name,
                              @RequestParam(required = false) String code,
                              @RequestParam(required = false) String description,
                              @RequestParam(required = false) String startDate,
                              @RequestParam(required = false) String endDate,
                              @RequestParam(required = false) BigDecimal totalBudget,
                              @RequestParam(required = false) Long grantCoordinatorId,
                              @RequestParam(required = false, defaultValue = "false") boolean active,
                              RedirectAttributes redirectAttributes) {
        try {
            Project project = new Project();
            project.setName(name.trim());
            project.setCode(blankToNull(code));
            project.setDescription(blankToNull(description));
            project.setStartDate(parseDate(startDate));
            project.setEndDate(parseDate(endDate));
            project.setTotalBudget(totalBudget);
            project.setGrantCoordinator(resolveEmployee(grantCoordinatorId));
            project.setActive(active);
            projectRepository.save(project);
            redirectAttributes.addFlashAttribute("successMessage", "Dodano projekt: " + project.getName());
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return sectionRedirect("/admin/projects");
        }
        return sectionRedirect("/admin/projects");
    }

    // --- Kategorie budżetowe ---

    @GetMapping("/budget-categories")
    public String budgetCategories() {
        return sectionRedirect("/dashboard");
    }

    @PostMapping("/budget-categories/{id}/move")
    public String moveBudgetCategory(@PathVariable Long id,
                                     @RequestParam int direction,
                                     @RequestParam(required = false) Integer year,
                                     @RequestParam(required = false) Integer month,
                                     RedirectAttributes redirectAttributes) {
        try {
            budgetSetupService.moveBudgetCategory(id, direction);
            redirectAttributes.addFlashAttribute("successMessage", "Zmieniono kolejność kategorii.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        int selectedYear = year != null ? year : java.time.LocalDate.now().getYear();
        if (month != null && month >= 1 && month <= 12) {
            return sectionRedirect("/dashboard/month?year=" + selectedYear + "&month=" + month);
        }
        return sectionRedirect("/dashboard?year=" + selectedYear);
    }

    // --- Nowy budżet ---

    @GetMapping("/budget/new")
    public String newBudget(Model model) {
        model.addAttribute("activeSection", "budget-new");
        model.addAttribute("existingYears", budgetSetupService.getExistingFiscalYears());
        model.addAttribute("nextYear", LocalDate.now().getYear() + 1);
        return "admin/budget-new";
    }


    private static String sectionRedirect(String path) {
        if (path.startsWith("/admin")) {
            path = BudgetSection.pageBase() + path.substring("/admin".length());
        } else if (path.startsWith("/dashboard")) {
            path = BudgetSection.currentBase() + path.substring("/dashboard".length());
        }
        return "redirect:" + path;
    }

    private void populateGrantForm(Model model, Grant grant, String pageTitle) {
        model.addAttribute("activeSection", "grants");
        model.addAttribute("grant", grant);
        model.addAttribute("grantCommand", toGrantSaveCommand(grant));
        model.addAttribute("pageTitle", pageTitle);
        model.addAttribute("sponsors", sponsorRepository.findAll());
        model.addAttribute("projects", projectRepository.findAll());
        model.addAttribute("employees", employeeRepository.findAll());
        model.addAttribute("contacts", sponsorContactRepository.findAllWithSponsor());
        model.addAttribute("budgetTemplates", budgetSetupService.listBudgetTemplates());
    }

    private GrantSaveCommand toGrantSaveCommand(Grant grant) {
        GrantSaveCommand command = new GrantSaveCommand();
        command.setId(grant.getId());
        command.setCode(grant.getCode());
        command.setName(grant.getName());
        if (grant.getSponsor() != null) {
            command.setSponsorId(grant.getSponsor().getId());
        }
        if (grant.getProject() != null) {
            command.setProjectId(grant.getProject().getId());
        }
        command.setStartDate(grant.getStartDate());
        command.setEndDate(grant.getEndDate());
        command.setTotalAmount(grant.getTotalAmount());
        command.setCurrency(grant.getCurrency() != null ? grant.getCurrency() : "PLN");
        if (grant.getPrimarySponsorContact() != null) {
            command.setPrimarySponsorContactId(grant.getPrimarySponsorContact().getId());
        }
        if (grant.getFinancialSponsorContact() != null) {
            command.setFinancialSponsorContactId(grant.getFinancialSponsorContact().getId());
        }
        if (grant.getGrantCoordinator() != null) {
            command.setGrantCoordinatorId(grant.getGrantCoordinator().getId());
        }
        command.setActive(grant.getId() == null || grant.isActive());
        Map<String, Long> templateIdByCode = budgetSetupService.listBudgetTemplates().stream()
                .filter(template -> template.getCode() != null)
                .collect(java.util.stream.Collectors.toMap(
                        template -> template.getCode().trim().toUpperCase(),
                        BudgetItemTemplate::getId,
                        (a, b) -> a));
        Map<String, Long> templateIdByName = budgetSetupService.listBudgetTemplates().stream()
                .filter(template -> template.getName() != null)
                .collect(java.util.stream.Collectors.toMap(
                        template -> template.getName().trim().toUpperCase(),
                        BudgetItemTemplate::getId,
                        (a, b) -> a));
        if (grant.getBudgetItems() != null) {
            grant.getBudgetItems().stream()
                    .filter(GrantBudgetItem::isActive)
                    .sorted(java.util.Comparator.comparing(GrantBudgetItem::getName, String.CASE_INSENSITIVE_ORDER))
                    .forEach(item -> {
                        GrantSaveCommand.BudgetItemCommand row = new GrantSaveCommand.BudgetItemCommand();
                        row.setCode(item.getCode());
                        row.setName(item.getName());
                        row.setPlannedAmount(item.getPlannedAmount());
                        if (item.getCode() != null) {
                            row.setTemplateId(templateIdByCode.get(item.getCode().trim().toUpperCase()));
                        }
                        if (row.getTemplateId() == null && item.getName() != null) {
                            row.setTemplateId(templateIdByName.get(item.getName().trim().toUpperCase()));
                        }
                        command.getBudgetItems().add(row);
                    });
        }
        if (grant.getTranches() != null) {
            grant.getTranches().stream()
                    .sorted(Comparator.comparingInt(GrantTranche::getTrancheNumber))
                    .forEach(tranche -> {
                        GrantSaveCommand.TrancheCommand row = new GrantSaveCommand.TrancheCommand();
                        row.setId(tranche.getId());
                        row.setTrancheNumber(tranche.getTrancheNumber());
                        row.setPlannedDate(tranche.getPlannedDate());
                        row.setPlannedAmount(tranche.getPlannedAmount());
                        row.setReceived(tranche.isReceived());
                        row.setReceivedDate(tranche.getReceivedDate());
                        row.setReceivedAmount(tranche.getReceivedAmount());
                        command.getTranches().add(row);
                    });
        }
        return command;
    }

    private void populateContactForm(Model model, SponsorContact contact, String pageTitle, Long defaultSponsorId) {
        model.addAttribute("activeSection", "sponsors");
        model.addAttribute("contact", contact);
        model.addAttribute("pageTitle", pageTitle);
        model.addAttribute("sponsors", sponsorRepository.findAll());
        model.addAttribute("defaultSponsorId", defaultSponsorId);
    }

    private void populateProjectForm(Model model, Project project, String pageTitle) {
        model.addAttribute("activeSection", "projects");
        model.addAttribute("project", project);
        model.addAttribute("pageTitle", pageTitle);
        model.addAttribute("employees", employeeRepository.findAll());
    }

    private SponsorContact resolveContact(Long id) {
        if (id == null) {
            return null;
        }
        return sponsorContactRepository.findById(id).orElse(null);
    }

    private Employee resolveEmployee(Long id) {
        if (id == null) {
            return null;
        }
        return employeeRepository.findById(id).orElse(null);
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
