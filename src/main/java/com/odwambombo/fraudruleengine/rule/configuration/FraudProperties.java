package com.odwambombo.fraudruleengine.rule.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "fraud")
@Validated
public class FraudProperties {

    @Valid
    private final Risk risk = new Risk();
    @Valid
    private final Rules rules = new Rules();

    @Getter
    @Setter
    public static class Risk {

        @Min(0)
        private int mediumThreshold = 30;
        @Min(0)
        private int highThreshold = 60;
        @Min(0)
        private int criticalThreshold = 80;
        @Min(0)
        private int flagThreshold = 60;

        @AssertTrue(message = "risk thresholds must increase from medium to high to critical")
        public boolean isThresholdOrderValid() {
            return mediumThreshold < highThreshold && highThreshold < criticalThreshold;
        }
    }

    @Getter
    @Setter
    public static class Rules {

        @Valid
        private final HighValue highValue = new HighValue();
        @Valid
        private final UnusualTime unusualTime = new UnusualTime();
        @Valid
        private final RiskyCategory riskyCategory = new RiskyCategory();
        @Valid
        private final ForeignTransaction foreignTransaction = new ForeignTransaction();
        @Valid
        private final Velocity velocity = new Velocity();

    }

    @Getter
    @Setter
    public static class HighValue {

        private boolean enabled = true;
        @NotNull
        @DecimalMin(value = "0.00", inclusive = false)
        private BigDecimal threshold = new BigDecimal("20000.00");
        @NotBlank
        @Pattern(regexp = "(?i)[A-Z]{3}")
        private String currency = "ZAR";
        @Positive
        private int score = 40;

    }

    @Getter
    @Setter
    public static class UnusualTime {

        private boolean enabled = true;
        @NotNull
        private LocalTime startInclusive = LocalTime.MIDNIGHT;
        @NotNull
        private LocalTime endExclusive = LocalTime.of(4, 0);
        @Positive
        private int score = 20;

        @AssertTrue(message = "unusual-time start and end must be different")
        public boolean isWindowValid() {
            return startInclusive != null && endExclusive != null && !startInclusive.equals(endExclusive);
        }
    }

    @Getter
    @Setter
    public static class RiskyCategory {

        private boolean enabled = true;
        @NotEmpty
        private Set<@NotBlank String> categories = new LinkedHashSet<>(Set.of("GAMBLING", "CRYPTOCURRENCY"));
        @Positive
        private int score = 15;

    }

    @Getter
    @Setter
    public static class ForeignTransaction {

        private boolean enabled = true;
        @Positive
        private int score = 25;

    }

    @Getter
    @Setter
    public static class Velocity {

        private boolean enabled = true;
        @Min(1)
        private int minimumTransactionCount = 5;
        @NotNull
        private Duration window = Duration.ofMinutes(10);
        @Positive
        private int score = 35;

        @AssertTrue(message = "velocity window must be positive")
        public boolean isWindowValid() {
            return window != null && !window.isZero() && !window.isNegative();
        }
    }
}
