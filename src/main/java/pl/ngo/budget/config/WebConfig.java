package pl.ngo.budget.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.math.BigDecimal;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ChangeAuditInterceptor changeAuditInterceptor;

    public WebConfig(ChangeAuditInterceptor changeAuditInterceptor) {
        this.changeAuditInterceptor = changeAuditInterceptor;
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addFormatterForFieldType(BigDecimal.class, new PolishBigDecimalFormatter());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(changeAuditInterceptor);
    }
}
