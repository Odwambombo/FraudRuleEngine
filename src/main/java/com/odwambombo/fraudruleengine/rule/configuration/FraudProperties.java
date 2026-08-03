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
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "fraud")
@Validated
public class FraudProperties {

    @Valid
    private final Risk risk = new Risk();
    @Valid
    private final Rules rules = new Rules();

    public Risk getRisk() {
        return risk;
    }

    public Rules getRules() {
        return rules;
    }

    public static class Risk {

        @Min(0)
        private int mediumThreshold = 30;
        @Min(0)
        private int highThreshold = 60;
        @Min(0)
        private int criticalThreshold = 80;
        @Min(0)
        private int flagThreshold = 60;

        public int getMediumThreshold() {
            return mediumThreshold;
        }

        public void setMediumThreshold(int mediumThreshold) {
            this.mediumThreshold = mediumThreshold;
        }

        public int getHighThreshold() {
            return highThreshold;
        }

        public void setHighThreshold(int highThreshold) {
            this.highThreshold = highThreshold;
        }

        public int getCriticalThreshold() {
            return criticalThreshold;
        }

        public void setCriticalThreshold(int criticalThreshold) {
            this.criticalThreshold = criticalThreshold;
        }

        public int getFlagThreshold() {
            return flagThreshold;
        }

        public void setFlagThreshold(int flagThreshold) {
            this.flagThreshold = flagThreshold;
        }

        @AssertTrue(message = "risk thresholds must increase from medium to high to critical")
        public boolean isThresholdOrderValid() {
            return mediumThreshold < highThreshold && highThreshold < criticalThreshold;
        }
    }

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

        public HighValue getHighValue() {
            return highValue;
        }

        public UnusualTime getUnusualTime() {
            return unusualTime;
        }

        public RiskyCategory getRiskyCategory() {
            return riskyCategory;
        }

        public ForeignTransaction getForeignTransaction() {
            return foreignTransaction;
        }

        public Velocity getVelocity() {
            return velocity;
        }
    }

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

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public BigDecimal getThreshold() {
            return threshold;
        }

        public void setThreshold(BigDecimal threshold) {
            this.threshold = threshold;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            this.score = score;
        }
    }

    public static class UnusualTime {

        private boolean enabled = true;
        @NotNull
        private LocalTime startInclusive = LocalTime.MIDNIGHT;
        @NotNull
        private LocalTime endExclusive = LocalTime.of(4, 0);
        @Positive
        private int score = 20;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public LocalTime getStartInclusive() {
            return startInclusive;
        }

        public void setStartInclusive(LocalTime startInclusive) {
            this.startInclusive = startInclusive;
        }

        public LocalTime getEndExclusive() {
            return endExclusive;
        }

        public void setEndExclusive(LocalTime endExclusive) {
            this.endExclusive = endExclusive;
        }

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            this.score = score;
        }

        @AssertTrue(message = "unusual-time start and end must be different")
        public boolean isWindowValid() {
            return startInclusive != null
                    && endExclusive != null
                    && !startInclusive.equals(endExclusive);
        }
    }

    public static class RiskyCategory {

        private boolean enabled = true;
        @NotEmpty
        private Set<@NotBlank String> categories =
                new LinkedHashSet<>(Set.of("GAMBLING", "CRYPTOCURRENCY"));
        @Positive
        private int score = 15;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Set<String> getCategories() {
            return categories;
        }

        public void setCategories(Set<String> categories) {
            this.categories = categories;
        }

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            this.score = score;
        }
    }

    public static class ForeignTransaction {

        private boolean enabled = true;
        @Positive
        private int score = 25;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            this.score = score;
        }
    }

    public static class Velocity {

        private boolean enabled = true;
        @Min(1)
        private int minimumTransactionCount = 5;
        @NotNull
        private Duration window = Duration.ofMinutes(10);
        @Positive
        private int score = 35;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMinimumTransactionCount() {
            return minimumTransactionCount;
        }

        public void setMinimumTransactionCount(int minimumTransactionCount) {
            this.minimumTransactionCount = minimumTransactionCount;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            this.score = score;
        }

        @AssertTrue(message = "velocity window must be positive")
        public boolean isWindowValid() {
            return window != null && !window.isZero() && !window.isNegative();
        }
    }
}
