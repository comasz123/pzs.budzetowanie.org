package pl.ngo.budget.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@AllArgsConstructor
public class HomeChartDto {

    private int year;
    private List<String> months;
    private List<BigDecimal> income;
    private List<BigDecimal> plannedCost;
}
