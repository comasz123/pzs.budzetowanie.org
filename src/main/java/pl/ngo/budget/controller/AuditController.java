package pl.ngo.budget.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import pl.ngo.budget.service.AuditService;

@Controller
@RequestMapping("/admin/dziennik")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("activeSection", "audit");
        model.addAttribute("events", auditService.latest());
        return "admin/audit";
    }
}
